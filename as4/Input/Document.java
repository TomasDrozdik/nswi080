import java.io.Serializable;
import java.util.ArrayList;

/**
 * Represents some kind of document that can be displayed to the user.
 */
public class Document implements DocumentInfo {
    private String name;
	private String content;
	private ArrayList<String> comments;
	private int viewCount;

	public Document(String name) {
		this.name = name;
	    this.content = null;
	    this.comments = new ArrayList<>();
	    this.viewCount = 0;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getContent() {
	    return content;
	}

	public void persist() {
		this.content = null;
	}

	public void increaseViewCount() {
		viewCount++;
	}

	public void comment(String comment) {
		comments.add(comment);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getViewCount() {
		return viewCount;
	}

	@Override
	public ArrayList<String> getComments() {
		return comments;
	}
}