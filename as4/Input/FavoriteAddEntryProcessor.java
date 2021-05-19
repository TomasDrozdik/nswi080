import com.hazelcast.map.EntryProcessor;

import java.util.Map;

public class FavoriteAddEntryProcessor implements EntryProcessor<String, ClientProfile, String> {
    @Override
    public String process(Map.Entry<String, ClientProfile> entry) {
        ClientProfile profile = entry.getValue();
        profile.addFavorite();
        entry.setValue(profile);
        return profile.getSelectedDocument();
    }
}
