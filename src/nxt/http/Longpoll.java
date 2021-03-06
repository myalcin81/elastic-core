package nxt.http;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.BlockchainProcessorImpl;
import nxt.Generator;
import nxt.NxtException;
import nxt.TransactionProcessorImpl;

// TODO, FIXME fix to forbid a memory overload DOS attack. Maybe hard-limit number of longpolls per IP?
// Otherwise user can create INT_MAX number of objects of type ExpiringListPointer in memory
// Another bad effect is that the longpoll causes to hang for 5 seconds even if user clicks cancel in the browser. Examine that please!

// CHECK FOR RACECONDITIONS (synchronized keywords) !!!!!!!!

class ClearTask extends TimerTask {
	private HashMap<Integer, ExpiringListPointer> toClear = null;
	private ArrayList<String> events = null;

	public ClearTask(final HashMap<Integer, ExpiringListPointer> h, final ArrayList<String> e) {
		this.toClear = h;
		this.events = e;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public void run() {
		if (this.toClear != null) {
			int minimalIndex = Integer.MAX_VALUE;
			Iterator it = this.toClear.entrySet().iterator();
			while (it.hasNext()) {
				@SuppressWarnings("unchecked")
				final HashMap.Entry<Integer, ExpiringListPointer> ptr = (HashMap.Entry<Integer, ExpiringListPointer>) it
						.next();
				if (ptr.getValue().expired()) {
					// System.out.println("Clearing inactive listener: "
					// + ptr.getKey());
					it.remove(); // avoids a ConcurrentModificationException
				} else {
					if (ExpiringListPointer.lastPosition < minimalIndex) {
						minimalIndex = ExpiringListPointer.lastPosition;
					}
				}
			}

			// strip events below minimalIndex, if applicable
			if ((minimalIndex > 0) && (minimalIndex != Integer.MAX_VALUE)) {
				this.events.subList(0, minimalIndex).clear();
			}

			// run again through iterator and adjust minimal indized
			it = this.toClear.entrySet().iterator();
			while (it.hasNext()) {
				final HashMap.Entry<Integer, ExpiringListPointer> ptr = (HashMap.Entry<Integer, ExpiringListPointer>) it
						.next();
				if ((minimalIndex > 0) && (minimalIndex != Integer.MAX_VALUE)) {
					ptr.getValue().normalizeIndex(minimalIndex);
				}
			}
		}

	}
}

final class ExpiringListPointer {
	static int lastPosition = 0;
	static int expireTime = 0;
	Date lastUpdated = null;

	public ExpiringListPointer(final int latestPosition, final int expireTimeLocal) {
		this.lastUpdated = new Date();
		ExpiringListPointer.lastPosition = latestPosition;
		ExpiringListPointer.expireTime = expireTimeLocal;
	}

	public boolean expired() {
		// ListPointers expire after 25 seconds
		final long seconds = ((new Date()).getTime() - this.lastUpdated.getTime()) / 1000;
		return seconds > (ExpiringListPointer.expireTime / 1000);
	}

	public void normalizeIndex(final int removed) {
		ExpiringListPointer.lastPosition = ExpiringListPointer.lastPosition - removed;
		if (ExpiringListPointer.lastPosition < 0) {
			ExpiringListPointer.lastPosition = 0;
		}
	}

	public void reuse(final int idx) {
		this.lastUpdated = new Date();
		ExpiringListPointer.lastPosition = idx;
	}
}

public final class Longpoll extends APIServlet.APIRequestHandler {

	static final int waitTimeValue = 5000;
	static final int garbageTimeout = 10000;
	static final int expireTime = 25000;
	static final Longpoll instance = new Longpoll();
	static final HashMap<Integer, ExpiringListPointer> setListings = new HashMap<>();
	static final ArrayList<String> eventQueue = new ArrayList<>();
	static final ClearTask clearTask = new ClearTask(Longpoll.setListings, Longpoll.eventQueue);
	static final Timer timer = new Timer();
	static boolean timerInitialized = false;

	private Longpoll() {
		super(new APITag[] { APITag.AE }, "nil");
		BlockchainProcessorImpl.getInstance().blockListeners.addListener(block -> {
			final String event = "block " + block.getHeight();
			final ArrayList<String> list = new ArrayList<>();
			list.add(event);
			Longpoll.instance.addEvents(list);
		}, nxt.BlockchainProcessor.Event.BLOCK_SCANNED);

		BlockchainProcessorImpl.getInstance().blockListeners.addListener(block -> {
			final String event = "new block (" + block.getHeight() + ")";
			final ArrayList<String> list = new ArrayList<>();
			list.add(event);
			Longpoll.instance.addEvents(list);
		}, nxt.BlockchainProcessor.Event.BLOCK_PUSHED);

		Generator.addListener(t -> {
			final String event = "generator updated";
			final ArrayList<String> list = new ArrayList<>();
			list.add(event);
			Longpoll.instance.addEvents(list);
		}, nxt.Generator.Event.GENERATION_DEADLINE);

		TransactionProcessorImpl.getInstance().addListener(t -> {
			final String event = "broadcast transaction";
			final ArrayList<String> list = new ArrayList<>();
			list.add(event);
			Longpoll.instance.addEvents(list);
		}, nxt.TransactionProcessor.Event.BROADCASTED_OWN_TRANSACTION);
	}

	synchronized public void addEvents(final List<String> l) {
		for (final String x : l) {
			Longpoll.eventQueue.add(x);
			// System.out.println("Adding: " + x);
		}

		synchronized (Longpoll.instance) {
			Longpoll.instance.notify();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {

		final JSONObject response = new JSONObject();

		final String randomIdStr = ParameterParser.getParameterMultipart(req, "randomId");
		int randomId;
		try {
			randomId = Integer.parseInt(randomIdStr);
		} catch (final NumberFormatException e) {
			response.put("error", "please provide a randomId (within the integer range)");
			return response;
		}

		ExpiringListPointer p = null;
		if (Longpoll.setListings.containsKey(randomId)) {
			// System.out.println("Reusing Linstener: " + randomId);
			p = Longpoll.setListings.get(randomId);
		} else {
			// System.out.println("Creating new Listener: " + randomId);
			synchronized (this) {
				p = new ExpiringListPointer(Longpoll.eventQueue.size(), Longpoll.expireTime);
				Longpoll.setListings.put(randomId, p);
			}
		}

		// Schedule timer if not done yet
		if (!Longpoll.timerInitialized) {
			// Schedule to run after every 3 second (3000 millisecond)
			try {
				Longpoll.timer.scheduleAtFixedRate(Longpoll.clearTask, 0, Longpoll.garbageTimeout);
				Longpoll.timerInitialized = true;
			} catch (final java.lang.IllegalStateException e) {
				Longpoll.timerInitialized = true; // TODO FIXME (WHY SOMETIMES
													// ITS ALREADY INITIALIZED)
			}

		}

		synchronized (this) {
			try {
				if (ExpiringListPointer.lastPosition == Longpoll.eventQueue.size()) {
					this.wait(Longpoll.waitTimeValue);
				}

				final JSONArray arr = new JSONArray();
				if (ExpiringListPointer.lastPosition >= Longpoll.eventQueue.size()) {
					// Timeout, nothing new, no notification
					response.put("event", "timeout");
					return response;
				}
				for (int i = ExpiringListPointer.lastPosition; i < Longpoll.eventQueue.size(); ++i) {
					arr.add(Longpoll.eventQueue.get(i));
				}
				// System.out.println(p.lastPosition);

				p.reuse(Longpoll.eventQueue.size());

				response.put("event", arr);
				return response;

			} catch (final InterruptedException e) {
				// Timeout, no notification
				response.put("event", "timeout");
				return response;
			}
		}
	}

}