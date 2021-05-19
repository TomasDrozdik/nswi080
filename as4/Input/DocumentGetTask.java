import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;

import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

class DocumentGetTask implements Callable, Serializable, HazelcastInstanceAware {
    private final String userName;
    private final String documentName;
    private transient HazelcastInstance hazelcast;

    public DocumentGetTask(String userName, String documentName) {
        this.userName = userName;
        this.documentName = documentName;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
    }

    public Document call() {
        Lock testLock = hazelcast.getCPSubsystem().getLock("documentFetch");
        testLock.lock();
        try {
            // Get the document
            IMap<String, Document> documentCache = hazelcast.getMap("documentCache");
            IMap<String, Document> documentMetadata = hazelcast.getMap("documentMetadata");

            Document document;
            if (documentCache.containsKey(documentName)) {
                document = documentCache.get(documentName);
            } else {
                if (documentMetadata.containsKey(documentName)) {
                    document = documentMetadata.get(documentName);
                } else {
                    document = new Document(documentName);
                }

                // Fetch the data since it was not in the cache
                document.setContent(DocumentContentFetch.fetchContent(documentName));
            }

            // Update document and store it, make sure there is a place for it
            document.increaseViewCount();
            documentCache.put(documentName, document);
            documentMetadata.put(documentName, document);

            // Update the client who requested this document
            IMap<String, ClientProfile> clientProfiles = hazelcast.getMap("clientProfiles");
            clientProfiles.executeOnKey(userName, new ClientSetSelectedEntryProcessor(documentName));
            assert(documentCache.containsKey(documentName) && document.getContent() != null);
            return document;
        } finally {
            testLock.unlock();
        }
    }
}
