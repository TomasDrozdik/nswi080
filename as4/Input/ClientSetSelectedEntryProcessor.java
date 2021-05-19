import com.hazelcast.map.EntryProcessor;

import java.util.Map;

public class ClientSetSelectedEntryProcessor implements EntryProcessor<String, ClientProfile, String> {
    private final String documentName;

    public ClientSetSelectedEntryProcessor(String documentName) {
        this.documentName = documentName;
    }

    @Override
    public String process(Map.Entry<String, ClientProfile> entry) {
        ClientProfile profile = entry.getValue();
        profile.setSelectedDocument(documentName);
        entry.setValue(profile);
        return documentName;
    }
}
