// SearchEvent.java
// (C) 2005 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 10.10.2005 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2006-04-02 22:40:07 +0200 (So, 02 Apr 2006) $
// $LastChangedRevision: 1986 $
// $LastChangedBy: orbiter $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import de.anomic.document.Condenser;
import de.anomic.kelondro.text.Segment;
import de.anomic.kelondro.text.metadataPrototype.URLMetadataRow;
import de.anomic.kelondro.util.SetTools;
import de.anomic.kelondro.util.SortStack;
import de.anomic.kelondro.util.SortStore;
import de.anomic.search.RankingProcess.NavigatorEntry;
import de.anomic.search.SnippetCache.MediaSnippet;
import de.anomic.server.serverProfiling;
import de.anomic.yacy.yacySeedDB;
import de.anomic.yacy.logging.Log;
import de.anomic.ymage.ProfilingGraph;

public class SnippetFetcher {

    protected final static int workerThreadCount = 10;
    
    // input values
    private final RankingProcess  rankedCache; // ordered search results, grows dynamically as all the query threads enrich this container
    private final QueryParams     query;
    private final Segment         indexSegment;
    private final yacySeedDB      peers;
    
    // result values
    protected       Worker[]                             workerThreads;
    protected final SortStore<ResultEntry>               result;
    protected final SortStore<SnippetCache.MediaSnippet> images; // container to sort images by size
    protected final HashMap<String, String>              failedURLs; // a mapping from a urlhash to a fail reason string
    protected final TreeSet<byte[]>                      snippetFetchWordHashes; // a set of word hashes that are used to match with the snippets
    long urlRetrievalAllTime;
    long snippetComputationAllTime;
    
    
    @SuppressWarnings("unchecked")
    SnippetFetcher(
            RankingProcess rankedCache,
            final QueryParams query,
            final Segment indexSegment,
            final yacySeedDB peers) {
    	
    	this.rankedCache = rankedCache;
    	this.query = query;
        this.indexSegment = indexSegment;
        this.peers = peers;
        
        this.urlRetrievalAllTime = 0;
        this.snippetComputationAllTime = 0;
        this.result = new SortStore<ResultEntry>(-1); // this is the result, enriched with snippets, ranked and ordered by ranking
        this.images = new SortStore<SnippetCache.MediaSnippet>(-1);
        this.failedURLs = new HashMap<String, String>(); // a map of urls to reason strings where a worker thread tried to work on, but failed.
        
        // snippets do not need to match with the complete query hashes,
        // only with the query minus the stopwords which had not been used for the search
        final TreeSet<byte[]> filtered = SetTools.joinConstructive(query.queryHashes, Switchboard.stopwordHashes);
        this.snippetFetchWordHashes = (TreeSet<byte[]>) query.queryHashes.clone();
        if ((filtered != null) && (filtered.size() > 0)) {
            SetTools.excludeDestructive(this.snippetFetchWordHashes, Switchboard.stopwordHashes);
        }
        
        // start worker threads to fetch urls and snippets
        this.workerThreads = new Worker[(query.onlineSnippetFetch) ? workerThreadCount : 1];
        for (int i = 0; i < this.workerThreads.length; i++) {
            this.workerThreads[i] = new Worker(i, 10000, (query.onlineSnippetFetch) ? 2 : 0);
            this.workerThreads[i].start();
        }
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), this.workerThreads.length + " online snippet fetch threads started", 0, 0), false);
        
    }

    public void restartWorker() {
    	if (anyWorkerAlive()) return;
    	this.workerThreads = new Worker[SearchEvent.workerThreadCount];
    	Worker worker;
        for (int i = 0; i < workerThreads.length; i++) {
            worker = new Worker(i, 6000, (query.onlineSnippetFetch) ? 2 : 0);
            worker.start();
            workerThreads[i] = worker;
        }
    }
    
    ResultEntry obtainResultEntry(final URLMetadataRow page, final int snippetFetchMode) {

        // a search result entry needs some work to produce a result Entry:
        // - check if url entry exists in LURL-db
        // - check exclusions, constraints, masks, media-domains
        // - load snippet (see if page exists) and check if snippet contains searched word

        // Snippet Fetching can has 3 modes:
        // 0 - do not fetch snippets
        // 1 - fetch snippets offline only
        // 2 - online snippet fetch
        
        // load only urls if there was not yet a root url of that hash
        // find the url entry

        long startTime = System.currentTimeMillis();
        final URLMetadataRow.Components metadata = page.metadata();
        final String pagetitle = metadata.dc_title().toLowerCase();
        if (metadata.url() == null) {
            registerFailure(page.hash(), "url corrupted (null)");
            return null; // rare case where the url is corrupted
        }
        final String pageurl = metadata.url().toString().toLowerCase();
        final String pageauthor = metadata.dc_creator().toLowerCase();
        final long dbRetrievalTime = System.currentTimeMillis() - startTime;
        
        // check exclusion
        if ((QueryParams.matches(pagetitle, query.excludeHashes)) ||
            (QueryParams.matches(pageurl, query.excludeHashes)) ||
            (QueryParams.matches(pageauthor, query.excludeHashes))) {
            return null;
        }
            
        // check url mask
        if (!(pageurl.matches(query.urlMask))) {
            return null;
        }
            
        // check constraints
        if ((query.constraint != null) &&
            (query.constraint.get(Condenser.flag_cat_indexof)) &&
            (!(metadata.dc_title().startsWith("Index of")))) {
            final Iterator<byte[]> wi = query.queryHashes.iterator();
            while (wi.hasNext()) try { indexSegment.termIndex().remove(wi.next(), page.hash()); } catch (IOException e) {}
            registerFailure(page.hash(), "index-of constraint not fullfilled");
            return null;
        }
        
        if ((query.contentdom == QueryParams.CONTENTDOM_AUDIO) && (page.laudio() == 0)) {
            registerFailure(page.hash(), "contentdom-audio constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_VIDEO) && (page.lvideo() == 0)) {
            registerFailure(page.hash(), "contentdom-video constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (page.limage() == 0)) {
            registerFailure(page.hash(), "contentdom-image constraint not fullfilled");
            return null;
        }
        if ((query.contentdom == QueryParams.CONTENTDOM_APP) && (page.lapp() == 0)) {
            registerFailure(page.hash(), "contentdom-app constraint not fullfilled");
            return null;
        }

        if (snippetFetchMode == 0) {
            return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, 0); // result without snippet
        }
        
        // load snippet
        if (query.contentdom == QueryParams.CONTENTDOM_TEXT) {
            // attach text snippet
            startTime = System.currentTimeMillis();
            final SnippetCache.TextSnippet snippet = SnippetCache.retrieveTextSnippet(metadata, snippetFetchWordHashes, (snippetFetchMode == 2), ((query.constraint != null) && (query.constraint.get(Condenser.flag_cat_indexof))), 180, (snippetFetchMode == 2) ? Integer.MAX_VALUE : 30000, query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH_EVENT", "text snippet load time for " + metadata.url() + ": " + snippetComputationTime + ", " + ((snippet.getErrorCode() < 11) ? "snippet found" : ("no snippet found (" + snippet.getError() + ")")));
            
            if (snippet.getErrorCode() < 11) {
                // we loaded the file and found the snippet
                return new ResultEntry(page, indexSegment, peers, snippet, null, dbRetrievalTime, snippetComputationTime); // result with snippet attached
            } else if (snippetFetchMode == 1) {
                // we did not demand online loading, therefore a failure does not mean that the missing snippet causes a rejection of this result
                // this may happen during a remote search, because snippet loading is omitted to retrieve results faster
                return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, snippetComputationTime); // result without snippet
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no text snippet for URL " + metadata.url());
                if (!peers.mySeed().isVirgin())
                    try {
                        SnippetCache.failConsequences(snippet, query.id(false));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                return null;
            }
        } else {
            // attach media information
            startTime = System.currentTimeMillis();
            final ArrayList<MediaSnippet> mediaSnippets = SnippetCache.retrieveMediaSnippets(metadata.url(), snippetFetchWordHashes, query.contentdom, (snippetFetchMode == 2), 6000, query.isGlobal());
            final long snippetComputationTime = System.currentTimeMillis() - startTime;
            Log.logInfo("SEARCH_EVENT", "media snippet load time for " + metadata.url() + ": " + snippetComputationTime);
            
            if ((mediaSnippets != null) && (mediaSnippets.size() > 0)) {
                // found media snippets, return entry
                return new ResultEntry(page, indexSegment, peers, null, mediaSnippets, dbRetrievalTime, snippetComputationTime);
            } else if (snippetFetchMode == 1) {
                return new ResultEntry(page, indexSegment, peers, null, null, dbRetrievalTime, snippetComputationTime);
            } else {
                // problems with snippet fetch
                registerFailure(page.hash(), "no media snippet for URL " + metadata.url());
                return null;
            }
        }
        // finished, no more actions possible here
    }
    
    boolean anyWorkerAlive() {
        if (this.workerThreads == null) return false;
        for (int i = 0; i < this.workerThreads.length; i++) {
           if ((this.workerThreads[i] != null) &&
               (this.workerThreads[i].isAlive()) &&
               (this.workerThreads[i].busytime() < 3000)) return true;
        }
        return false;
    }
    
    
    public long getURLRetrievalTime() {
        return this.urlRetrievalAllTime;
    }
    
    public long getSnippetComputationTime() {
        return this.snippetComputationAllTime;
    }

    protected class Worker extends Thread {
        
        private final long timeout; // the date until this thread should try to work
        private long lastLifeSign; // when the last time the run()-loop was executed
        private final int id;
        private int snippetMode;
        
        public Worker(final int id, final long maxlifetime, int snippetMode) {
            this.id = id;
            this.snippetMode = snippetMode;
            this.lastLifeSign = System.currentTimeMillis();
            this.timeout = System.currentTimeMillis() + Math.max(1000, maxlifetime);
        }

        public void run() {

            // start fetching urls and snippets
            URLMetadataRow page;
            final int fetchAhead = snippetMode == 0 ? 0 : 10;
            boolean nav_topics = query.navigators.equals("all") || query.navigators.indexOf("topics") >= 0;
            try {
                while (System.currentTimeMillis() < this.timeout) {
                    this.lastLifeSign = System.currentTimeMillis();
    
                    // check if we have enough
                    if ((query.contentdom == QueryParams.CONTENTDOM_IMAGE) && (images.size() >= query.neededResults() + fetchAhead)) break;
                    if ((query.contentdom != QueryParams.CONTENTDOM_IMAGE) && (result.size() >= query.neededResults() + fetchAhead)) break;
    
                    // get next entry
                    page = rankedCache.bestURL(true, 10000);
                    if (page == null) break;
                    if (result.exists(page.hash().hashCode())) continue;
                    if (failedURLs.get(page.hash()) != null) continue;
                    
                    final ResultEntry resultEntry = obtainResultEntry(page, snippetMode);
                    if (resultEntry == null) continue; // the entry had some problems, cannot be used
                    urlRetrievalAllTime += resultEntry.dbRetrievalTime;
                    snippetComputationAllTime += resultEntry.snippetComputationTime;
                    //System.out.println("+++DEBUG-resultWorker+++ fetched " + resultEntry.urlstring());
                    
                    // place the result to the result vector
                    if (!result.exists(resultEntry)) {
                        result.push(resultEntry, Long.valueOf(rankedCache.getOrder().cardinal(resultEntry.word())));
                        if (nav_topics) rankedCache.addTopics(resultEntry);
                    }
                    //System.out.println("DEBUG SNIPPET_LOADING: thread " + id + " got " + resultEntry.url());
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            Log.logInfo("SEARCH", "resultWorker thread " + id + " terminated");
        }
        
        public long busytime() {
            return System.currentTimeMillis() - this.lastLifeSign;
        }
    }
    
    private void registerFailure(final String urlhash, final String reason) {
        this.failedURLs.put(urlhash, reason);
        Log.logInfo("search", "sorted out hash " + urlhash + " during search: " + reason);
    }
    
    public ArrayList<NavigatorEntry> getHostNavigator(int maxentries) {
        return this.rankedCache.getHostNavigator(maxentries);
    }
    
    public ArrayList<NavigatorEntry> getTopicNavigator(final int maxentries) {
        // returns a set of words that are computed as toplist
        return this.rankedCache.getTopicNavigator(maxentries);
    }
    
    public ArrayList<NavigatorEntry> getAuthorNavigator(final int maxentries) {
        // returns a list of authors so far seen on result set
        return this.rankedCache.getAuthorNavigator(maxentries);
    }
    
    public int resultCount() {
    	return this.result.size();
    }
    
    public ResultEntry oneResult(final int item) {
        // check if we already retrieved this item
    	// (happens if a search pages is accessed a second time)
        serverProfiling.update("SEARCH", new ProfilingGraph.searchEvent(query.id(true), "obtain one result entry - start", 0, 0), false);
        if (this.result.sizeStore() > item) {
            // we have the wanted result already in the result array .. return that
            return this.result.element(item).element;
        }
        
        // finally wait until enough results are there produced from the
        // snippet fetch process
        while ((anyWorkerAlive()) && (result.size() <= item)) {
            try {Thread.sleep(item * 50L);} catch (final InterruptedException e) {}
        }

        // finally, if there is something, return the result
        if (this.result.size() <= item) return null;
        return this.result.element(item).element;
    }
    
    private int resultCounter = 0;
    public ResultEntry nextResult() {
        final ResultEntry re = oneResult(resultCounter);
        resultCounter++;
        return re;
    }
    
    public SnippetCache.MediaSnippet oneImage(final int item) {
        // check if we already retrieved this item (happens if a search pages is accessed a second time)
        if (this.images.sizeStore() > item) {
            // we have the wanted result already in the result array .. return that
            return this.images.element(item).element;
        }
        
        // feed some results from the result stack into the image stack
        final int count = Math.min(5, Math.max(1, 10 * this.result.size() / (item + 1)));
        for (int i = 0; i < count; i++) {
            // generate result object
            final ResultEntry result = nextResult();
            SnippetCache.MediaSnippet ms;
            if (result != null) {
                // iterate over all images in the result
                final ArrayList<SnippetCache.MediaSnippet> imagemedia = result.mediaSnippets();
                if (imagemedia != null) {
                    for (int j = 0; j < imagemedia.size(); j++) {
                        ms = imagemedia.get(j);
                        images.push(ms, Long.valueOf(ms.ranking));
                    }
                }
            }
        }
        
        // now take the specific item from the image stack
        if (this.images.size() <= item) return null;
        return this.images.element(item).element;
    }
    
    public ArrayList<SortStack<ResultEntry>.stackElement> completeResults(final long waitingtime) {
        final long timeout = System.currentTimeMillis() + waitingtime;
        while ((result.size() < query.neededResults()) && (anyWorkerAlive()) && (System.currentTimeMillis() < timeout)) {
            try {Thread.sleep(100);} catch (final InterruptedException e) {}
            //System.out.println("+++DEBUG-completeResults+++ sleeping " + 200);
        }
        return this.result.list(this.result.size());
    }
    
}
