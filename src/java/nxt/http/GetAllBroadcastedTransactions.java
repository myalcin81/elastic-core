/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package nxt.http;

import javax.servlet.http.HttpServletRequest;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import nxt.Nxt;
import nxt.Transaction;

public final class GetAllBroadcastedTransactions extends APIServlet.APIRequestHandler {

    static final GetAllBroadcastedTransactions instance = new GetAllBroadcastedTransactions();

    private GetAllBroadcastedTransactions() {
        super(new APITag[] {APITag.DEBUG});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        JSONArray jsonArray = new JSONArray();
        response.put("transactions", jsonArray);
        Transaction[] transactions = Nxt.getTransactionProcessor().getAllBroadcastedTransactions();
        for (Transaction transaction : transactions) {
            jsonArray.add(JSONData.unconfirmedTransaction(transaction));
        }
        return response;
    }
    
    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

}
