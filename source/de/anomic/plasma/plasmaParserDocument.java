//plasmaParserDocument.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//last major change: 24.04.2005
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//Using this software in any meaning (reading, learning, copying, compiling,
//running) means that you agree that the Author(s) is (are) not responsible
//for cost, loss of data or any harm that may be caused directly or indirectly
//by usage of this softare or this documentation. The usage of this software
//is on your own risk. The installation and usage (starting/running) of this
//software may allow other people or application to access your computer and
//any attached devices and is highly dependent on the configuration of the
//software which must be done by the user of the software; the author(s) is
//(are) also not responsible for proper configuration and usage of the
//software, even if provoked by documentation provided together with
//the software.
//
//Any changes to this file according to the GPL as documented in the file
//gpl.txt aside this file in the shipment you received can be done to the
//lines that follows this copyright notice here, but changes must not be
//done inside the copyright notive above. A re-distribution must contain
//the intact and unchanged copyright notice.
//Contributions and changes to the program code must be marked as such.

package de.anomic.plasma;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import de.anomic.server.serverFileUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.net.URL;

public class plasmaParserDocument {
    
    URL location;       // the source url
    String mimeType;    // mimeType as taken from http header
    String charset;     // the charset of the document
    String[] keywords;  // most resources provide a keyword field
    String shortTitle;  // a shortTitle mostly appears in the window header (border)
    String longTitle;   // the real title of the document, commonly h1-tags
    String[] sections;  // if present: more titles/headlines appearing in the document
    String abstrct;     // an abstract, if present: short content description
    private Object text;  // the clear text, all that is visible
    Map anchors;        // all links embedded as clickeable entities (anchor tags)
    TreeSet images;     // all visible pictures in document
    // the anchors and images - Maps are URL-to-EntityDescription mappings.
    // The EntityDescription appear either as visible text in anchors or as alternative
    // text in image tags.
    Map hyperlinks;
    Map medialinks;
    Map emaillinks;
    plasmaCondenser condenser;
    boolean resorted;
                    
    public plasmaParserDocument(URL location, String mimeType, String charset,
                    String[] keywords, String shortTitle, String longTitle,
                    String[] sections, String abstrct,
                    byte[] text, Map anchors, TreeSet images) {
        this.location = location;
        this.mimeType = (mimeType==null)?"application/octet-stream":mimeType;
        this.charset = charset;
        this.keywords = (keywords==null) ? new String[0] : keywords;
        this.shortTitle = (shortTitle==null)?"":shortTitle;
        this.longTitle = (longTitle==null)?"":longTitle;
        this.sections = (sections==null)?new String[0]:sections;
        this.abstrct = (abstrct==null)?"":abstrct;
        this.text = (text==null)?new byte[0]:text;
        this.anchors = (anchors==null)?new HashMap(0):anchors;
        this.images = (images==null)?new TreeSet():images;
        this.hyperlinks = null;
        this.medialinks = null;
        this.emaillinks = null;
        this.condenser = null;
        this.resorted = false;
    }
    
    public plasmaParserDocument(URL location, String mimeType, String charset,
            String[] keywords, String shortTitle, String longTitle,
            String[] sections, String abstrct,
            File text, Map anchors, TreeSet images) {
        this.location = location;
        this.mimeType = (mimeType==null)?"application/octet-stream":mimeType;
        this.charset = charset;
        this.keywords = (keywords==null) ? new String[0] : keywords;
        this.shortTitle = (shortTitle==null)?"":shortTitle;
        this.longTitle = (longTitle==null)?"":longTitle;
        this.sections = (sections==null)?new String[0]:sections;
        this.abstrct = (abstrct==null)?"":abstrct;
        this.text = text;
        if (text != null) text.deleteOnExit();
        this.anchors = (anchors==null)?new HashMap(0):anchors;
        this.images = (images==null)?new TreeSet():images;
        this.hyperlinks = null;
        this.medialinks = null;
        this.emaillinks = null;
        this.condenser = null;
        this.resorted = false;
    }    

    public String getMimeType() {
        return this.mimeType;
    }
    
    /**
     * @return the supposed charset of this document or <code>null</code> if unknown
     */
    public String getSourceCharset() {
        return this.charset;
    }
    
    public String getMainShortTitle() {
        if (shortTitle != null) return shortTitle; else return longTitle;
    }
    
    public String getMainLongTitle() {
        if (longTitle != null) return longTitle; else return shortTitle;
    }
    
    public String[] getSectionTitles() {
        if (sections != null) return sections; else return new String[]{getMainLongTitle()};
    }

    public String getAbstract() {
        if (abstrct != null) return abstrct; else return getMainLongTitle();
    }
    
    public InputStream getText() {
        try {
            if (this.text == null) return null;

            if (this.text instanceof File) return new BufferedInputStream(new FileInputStream((File)this.text));
            else if (this.text instanceof byte[]) return new ByteArrayInputStream((byte[])this.text);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; 
    }
    
    public byte[] getTextBytes() {
        try {
            if (this.text == null) return new byte[0];

            if (this.text instanceof File) return serverFileUtils.read((File)this.text);
            else if (this.text instanceof byte[]) return (byte[])this.text;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];             
    }
    
    public long getTextLength() {
        if (this.text == null) return 0;
        if (this.text instanceof File) return ((File)this.text).length();
        else if (this.text instanceof byte[]) return ((byte[])this.text).length;
        
        return -1; 
    }
    
    public plasmaCondenser getCondenser() {
        if (condenser == null) condenser = new plasmaCondenser(getText(), 0, 0);
        return condenser;
    }
    
    public String[] getSentences() {
        return getCondenser().sentences();
    }
    
    public String getKeywords(char separator) {
        // sort out doubles and empty words
        TreeSet hs = new TreeSet();
        String s;
        for (int i = 0; i < this.keywords.length; i++) {
            if (this.keywords[i] == null) continue;
            s = this.keywords[i].trim();
            if (s.length() > 0) hs.add(s.toLowerCase());
        }
        if (hs.size() == 0) return "";
        // generate a new list
        StringBuffer sb = new StringBuffer(this.keywords.length * 6);
        Iterator i = hs.iterator();
        while (i.hasNext()) sb.append((String) i.next()).append(separator);
        return sb.substring(0, sb.length() - 1);
    }
    
    public Map getAnchors() {
        // returns all links embedded as anchors (clickeable entities)
        return anchors;
    }
    
    public TreeSet getImages() {
        // returns all links enbedded as pictures (visible in document)
        // this resturns a htmlFilterImageEntry collection
        if (!resorted) resortLinks();
        return images;
    }
    
    // the next three methods provide a calculated view on the getAnchors/getImages:
    
    public Map getHyperlinks() {
        // this is a subset of the getAnchor-set: only links to other hyperrefs
        if (!resorted) resortLinks();
        return hyperlinks;
    }
    
    public Map getMedialinks() {
        // this is partly subset of getAnchor and getImage: all non-hyperrefs
        if (!resorted) resortLinks();
        return medialinks;
    }
    
    public Map getEmaillinks() {
        // this is part of the getAnchor-set: only links to email addresses
        if (!resorted) resortLinks();
        return emaillinks;
    }
    
    private synchronized void resortLinks() {
        
        // extract hyperlinks, medialinks and emaillinks from anchorlinks
        Iterator i;
        String url;
        int extpos, qpos;
        String ext = null;
        i = anchors.entrySet().iterator();
        hyperlinks = new HashMap();
        medialinks = new HashMap();
        emaillinks = new HashMap();
        TreeSet collectedImages = new TreeSet(); // this is a set that is collected now and joined later to the imagelinks
        Map.Entry entry;
        while (i.hasNext()) {
            entry = (Map.Entry) i.next();
            url = (String) entry.getKey();
            if ((url != null) && (url.startsWith("mailto:"))) {
                emaillinks.put(url.substring(7), entry.getValue());
            } else {
                extpos = url.lastIndexOf(".");
                String normal;
                if (extpos > 0) {
                    if (((qpos = url.indexOf("?")) >= 0) && (qpos > extpos)) {
                        ext = url.substring(extpos, qpos).toLowerCase();
                    } else {
                        ext = url.substring(extpos).toLowerCase();
                    }
                    try {normal = new URL(url).toNormalform();} catch (MalformedURLException e1) {
                        normal = null;
                    }
                    if (normal != null) { //TODO: extension function is not correct
                        if (plasmaParser.mediaExtContains(ext.substring(1))) {
                            // this is not a normal anchor, its a media link
                            medialinks.put(normal, entry.getValue());
                        } else {
                            hyperlinks.put(normal, entry.getValue());
                        }
                        if (plasmaParser.imageExtContains(ext.substring(1))) {
                            try {
                                collectedImages.add(new htmlFilterImageEntry(new URL(normal), "", -1, -1));
                            } catch (MalformedURLException e) {}
                        }
                    }
                }
            }
        }
        
        // add the images to the medialinks
        i = images.iterator();
        String normal;
        htmlFilterImageEntry iEntry;
        while (i.hasNext()) {
            iEntry = (htmlFilterImageEntry) i.next();
            normal = iEntry.url().toNormalform();
            if (normal != null) medialinks.put(normal, iEntry.alt()); // avoid NullPointerException
        }
        
        // expand the hyperlinks:
        // we add artificial hyperlinks to the hyperlink set
        // that can be calculated from given hyperlinks and imagelinks
        hyperlinks.putAll(plasmaParser.allReflinks(hyperlinks));
        hyperlinks.putAll(plasmaParser.allReflinks(medialinks));
        hyperlinks.putAll(plasmaParser.allSubpaths(hyperlinks));
        hyperlinks.putAll(plasmaParser.allSubpaths(medialinks));
        
        // finally add image links that we collected from the anchors to the image map
        i = collectedImages.iterator();
        while (i.hasNext()) {
            iEntry = (htmlFilterImageEntry) i.next();
            if (!images.contains(iEntry)) images.add(iEntry);
        }
        
        // don't do this again
        this.resorted = true;
    }
    
    public void close() {
        // delete the temp file
        if ((this.text != null) && (this.text instanceof File)) {
            try { ((File)this.text).delete(); } catch (Exception e) {/* ignore this */}
        }        
    }
    
    protected void finalize() throws Throwable {
        this.close();
        super.finalize();
    }
    
}
