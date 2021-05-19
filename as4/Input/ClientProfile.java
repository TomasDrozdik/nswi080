import java.io.Serializable;
import java.util.ArrayList;

public class ClientProfile implements Serializable {
    private final String userName;
    private ArrayList<String> favorites = new ArrayList<>();
    private int currentFavoriteIdx = 0;
    private String selectedDocument = null;

    ClientProfile(String userName) {
        this.userName = userName;
    }

    public void setSelectedDocument(String documentName) {
        this.selectedDocument = documentName;
    }

    public String getSelectedDocument() {
        //if (selectedDocument == null) {
        //    throw new RuntimeException("ERROR: There is no selected document.");
        //}
        return selectedDocument;
    }

    public ArrayList<String> getFavorites() {
        return favorites;
    }

    public void addFavorite() {
        if (selectedDocument != null) {
            if (!favorites.contains(selectedDocument)) {
                favorites.add(selectedDocument);
            }
        }
    }

    public void removeFavorite() {
        if (selectedDocument != null) {
            if (favorites.contains(selectedDocument)) {
                favorites.remove(selectedDocument);
            }
        }
    }

    public String getNextFavorite() {
        if (favorites.size() == 0) {
            return null;
        }
        currentFavoriteIdx = favorites.indexOf(selectedDocument);
        if (currentFavoriteIdx == -1) {
            currentFavoriteIdx = 0;
        } else {
            currentFavoriteIdx = (currentFavoriteIdx + 1) % favorites.size();
        }
        System.out.printf("Getting next favorite document [%d]%s%n", currentFavoriteIdx, favorites.get(currentFavoriteIdx));
        return favorites.get(currentFavoriteIdx);
    }
}
