import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;

import java.util.Map;
import java.util.concurrent.Callable;

class DocumentCommentEntryProcessor implements EntryProcessor<String, Document, String> {
    private final String commentText;

    public DocumentCommentEntryProcessor(String commentText) {
        this.commentText = commentText;
    }

    @Override
    public String process(Map.Entry<String, Document> entry) {
        Document document = entry.getValue();
        document.comment(commentText);
        entry.setValue(document);
        return entry.getValue().getName();
    }
}
