import java.io.Serializable;
import java.util.ArrayList;

public interface DocumentInfo extends Serializable {
    public String getName();
    public int getViewCount();
    public ArrayList<String> getComments();
}
