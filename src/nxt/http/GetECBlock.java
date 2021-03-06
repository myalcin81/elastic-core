/******************************************************************************
 * Copyright © 2013-2016 The XEL Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * XEL software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Block;
import nxt.Nxt;
import nxt.NxtException;

public final class GetECBlock extends APIServlet.APIRequestHandler {

	static final GetECBlock instance = new GetECBlock();

	private GetECBlock() {
		super(new APITag[] { APITag.BLOCKS }, "timestamp");
	}

	@Override
	protected JSONStreamAware processRequest(final HttpServletRequest req) throws NxtException {
		int timestamp = ParameterParser.getTimestamp(req);
		if (timestamp == 0) {
			timestamp = Nxt.getEpochTime();
		}
		final Block ecBlock = Nxt.getBlockchain().getECBlock(timestamp);
		final JSONObject response = new JSONObject();
		response.put("ecBlockId", ecBlock.getStringId());
		response.put("ecBlockHeight", ecBlock.getHeight());
		response.put("timestamp", timestamp);
		return response;
	}

}