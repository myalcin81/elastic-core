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

package nxt;

import nxt.db.BasicDb;
import nxt.db.TransactionalDb;

public final class Db {

	public static final String PREFIX = Constants.isTestnet ? "nxt.testDb" : "nxt.db";
	public static final TransactionalDb db = new TransactionalDb(new BasicDb.DbProperties()
			.maxCacheSize(Nxt.getIntProperty("nxt.dbCacheKB")).dbUrl(Nxt.getStringProperty(Db.PREFIX + "Url"))
			.dbType(Nxt.getStringProperty(Db.PREFIX + "Type")).dbDir(Nxt.getStringProperty(Db.PREFIX + "Dir"))
			.dbParams(Nxt.getStringProperty(Db.PREFIX + "Params"))
			.dbUsername(Nxt.getStringProperty(Db.PREFIX + "Username"))
			.dbPassword(Nxt.getStringProperty(Db.PREFIX + "Password", null, true))
			.maxConnections(Nxt.getIntProperty("nxt.maxDbConnections"))
			.loginTimeout(Nxt.getIntProperty("nxt.dbLoginTimeout"))
			.defaultLockTimeout(Nxt.getIntProperty("nxt.dbDefaultLockTimeout") * 1000)
			.maxMemoryRows(Nxt.getIntProperty("nxt.dbMaxMemoryRows")));

	static void init() {
		Db.db.init(new NxtDbVersion());
	}

	static void shutdown() {
		Db.db.shutdown();
	}

	private Db() {
	} // never

}
