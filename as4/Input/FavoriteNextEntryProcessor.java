import com.hazelcast.map.EntryProcessor;

import java.util.Map;

public class FavoriteNextEntryProcessor implements EntryProcessor<String, ClientProfile, String> {
    @Override
    public String process(Map.Entry<String, ClientProfile> entry) {
        ClientProfile profile = entry.getValue();
        String nextDocument = profile.getNextFavorite();
        entry.setValue(profile);
        return nextDocument;
    }
}
