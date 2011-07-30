package org.kontalk.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.kontalk.crypto.Coder;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.util.Log;


/**
 * A client for the polling service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class PollingClient extends AbstractClient {
    private static final String TAG = PollingClient.class.getSimpleName();

    private final String mMyNumber;

    public PollingClient(Context context, EndpointServer server, String token, String myNumber) {
        super(context, server, token);
        mMyNumber = myNumber;
    }

    /**
     * Polls the server for new messages.
     * @throws IOException
     */
    public List<AbstractMessage<?>> poll() throws IOException {

        List<AbstractMessage<?>> list = null;
        try {
            // http request!
            currentRequest = mServer.preparePolling(mAuthToken);
            HttpResponse response = mServer.execute(currentRequest);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            /*
            String xmlContent = EntityUtils.toString(response.getEntity());
            StringReader reader = new StringReader(xmlContent);
            InputSource inputSource = new InputSource(reader);
            */

            Document doc = builder.parse(response.getEntity().getContent());
            //reader.close();

            Element body = doc.getDocumentElement();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = (Node) children.item(i);
                if ("m".equals(node.getNodeName())) {
                    String id = null;
                    String origId = null;
                    String from = null;
                    String text = null;
                    String mime = null;
                    String fetchUrl = null;
                    List<String> group = null;

                    // FIXME handle empty node values

                    // message!
                    NodeList msgChildren = node.getChildNodes();
                    for (int j = 0; j < msgChildren.getLength(); j++) {
                        Element n2 = (Element) msgChildren.item(j);
                        if ("i".equals(n2.getNodeName()))
                            id = n2.getFirstChild().getNodeValue();
                        else if ("s".equals(n2.getNodeName()))
                            from = n2.getFirstChild().getNodeValue();
                        else if ("c".equals(n2.getNodeName())) {
                            text = n2.getFirstChild().getNodeValue();
                            mime = n2.getAttribute("t");
                            fetchUrl = n2.getAttribute("u");
                        }
                        else if ("g".equals(n2.getNodeName())) {
                            if (group == null)
                                group = new ArrayList<String>();
                            group.add(n2.getFirstChild().getNodeValue());
                        }
                        else if ("o".equals(n2.getNodeName())) {
                            origId = n2.getFirstChild().getNodeValue();
                        }
                    }

                    if (id != null && from != null && text != null && mime != null) {
                        // add the message to the list
                        AbstractMessage<?> msg = null;
                        String realId = null;

                        // use the originating id as the message id to match with message in database
                        if (origId != null) {
                            realId = id;
                            id = origId;
                        }

                        // Base64-decode the text
                        byte[] content = Base64.decode(text, Base64.DEFAULT);

                        // flag for left encrypted message
                        boolean encrypted = false;
                        // flag for originally encrypted message
                        boolean origEncrypted = false;
                        if (mime != null && mime.startsWith(AbstractMessage.ENC_MIME_PREFIX)) {
                            origEncrypted = true;
                            Coder coder = MessagingPreferences.getDecryptCoder(mContext, mMyNumber);
                            try {
                                content = coder.decrypt(content);
                            }
                            catch (Exception e) {
                                // pass over the message even if encrypted
                                // UI will warn the user about that and wait
                                // for user decisions
                                Log.e(TAG, "decryption failed", e);
                                encrypted = true;
                                content = text.getBytes();
                            }
                            // cut off the enc: part anyway
                            mime = mime.substring(AbstractMessage.ENC_MIME_PREFIX.length());
                        }

                        // plain text message
                        if (mime == null || PlainTextMessage.supportsMimeType(mime)) {
                            msg = new PlainTextMessage(mContext, id, from, content, group);
                        }

                        // message receipt
                        else if (ReceiptMessage.supportsMimeType(mime)) {
                            msg = new ReceiptMessage(mContext, id, from, content, group);
                        }

                        // image message
                        else if (ImageMessage.supportsMimeType(mime)) {
                            // extra argument: mime (first parameter)
                            msg = new ImageMessage(mContext, mime, id, from, content, group);
                        }

                        // TODO else other mime types

                        if (msg != null) {
                            // set the real message id
                            msg.setRealId(realId);

                            // remember encryption! :)
                            if (encrypted)
                                msg.setEncrypted();
                            if (origEncrypted)
                                msg.setWasEncrypted(true);

                            // set the fetch url (if any)
                            Log.d(TAG, "using fetch url: " + fetchUrl);
                            msg.setFetchUrl(fetchUrl);

                            if (list == null)
                                list = new ArrayList<AbstractMessage<?>>();
                            list.add(msg);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            IOException ie = new IOException("parse error");
            ie.initCause(e);
            throw ie;
        }
        finally {
            currentRequest = null;
        }

        return list;
    }
}