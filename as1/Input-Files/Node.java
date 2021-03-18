import java.util.Map;
import java.util.Set;

public interface Node {
	/** Gets the set of nodes connected to this node by an edge. */
	Set<Node> getNeighbors();
	/** Gets the set of nodes connected to this node by a path up to the specified distance. */
	Map<Node, Integer> getTransitiveNeighbors(int distance);
	/** Connects this node to another by an edge. */
	void addNeighbor(Node neighbor);
}
