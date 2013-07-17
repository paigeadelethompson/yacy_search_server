
/**
 * IndexReIndexMonitor_p Copyright 2013 by Michael Peter Christen First released
 * 29.04.2013 at http://yacy.net
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt If not, see
 * <http://www.gnu.org/licenses/>.
 */
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.migration;

import net.yacy.search.Switchboard;
import net.yacy.search.index.ReindexSolrBusyThread;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexReIndexMonitor_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        final Switchboard sb = (Switchboard) env;
        final serverObjects prop = new serverObjects();

        prop.put("docsprocessed", "0");
        prop.put("currentselectquery","");
        BusyThread bt = sb.getThread("reindexSolr");
        if (bt != null) {
            prop.put("querysize", bt.getJobCount());

            if (bt instanceof ReindexSolrBusyThread) {                
                prop.put("docsprocessed", ((ReindexSolrBusyThread) bt).getProcessed());
                prop.put("currentselectquery","q="+((ReindexSolrBusyThread) bt).getCurrentQuery());
            }
            
            if (post != null && post.containsKey("stopreindex")) {
                sb.terminateThread("reindexSolr", false);
                prop.put("infomessage", "reindex job stopped");
                prop.put("showstartbutton", 1);
            } else {
                prop.put("infomessage", "reindex is running");
                prop.put("showstartbutton", 0);
            }            
        } else {
            if (post != null && post.containsKey("reindexnow") && sb.index.fulltext().connectedLocalSolr()) {
                migration.reindexToschema(sb);
                prop.put("showstartbutton", 0);
                prop.put("querysize", "0");
                prop.put("infomessage","reindex job started");
            } else {
                prop.put("showstartbutton", 1);
                prop.put("querysize", "is empty");
                prop.put("infomessage", "no reindex job running");
            }
        }
        // return rewrite properties
        return prop;
    }
}
