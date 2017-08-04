import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * Created by Tam on 4/15/2016.
 */
public class Aalgorithm {
    private Node end;
    private Node start;
    private LinkedList<Long> sol = new LinkedList<>();
    private SearchNode current;

    public Aalgorithm(Node start, Node end) {
        this.end = end;
        this.start = start;
    }

    public class SearchNode implements Comparable<SearchNode> {

        private SearchNode prev;
        private double distance;
        private double priority;
        private Node node;

        public SearchNode(SearchNode prev, double distance, Node node) {
            this.prev = prev;
            this.distance = distance;
            this.node = node;
            priority = distance + distance(node.getLon(),
                    node.getLat(), end.getLon(), end.getLat());
        }

        public Node getNode() {
            return node;
        }

        public double getDistance() {
            return distance;
        }

        public SearchNode getPrev() {
            return prev;
        }

        public double getPriority() {
            return priority;
        }

        public int compareTo(SearchNode n) {
            if (n == null) {
                return -1;
            } else if (priority < n.getPriority()) {
                return -1;
            } else if (priority > n.getPriority()) {
                return 1;
            } else {
                return 0;
            }
        }

        public double distance(double lon1, double lat1,
                               double lon2, double lat2) {
            return Math.sqrt(Math.pow(Math.abs(lon2 - lon1), 2)
                    + Math.pow(Math.abs(lat2 - lat1), 2));
        }
    }

    public LinkedList<Long> findPath() {
        PriorityQueue<SearchNode> queue = new PriorityQueue<>();
        start.setMark(true);
        current = new SearchNode(null, 0, start);
        queue.add(current);
        while (current.getNode().getId() != end.getId()) {
            current = queue.remove();
            current.getNode().setMark(true);
            for (Node c: current.getNode().getConnections()) {
                if (!c.isMarked()) {
                    double d = distance(current.getNode().getLat(), current.getNode().getLon(),
                            c.getLat(), c.getLon());
                    SearchNode s = new SearchNode(current, current.getDistance() + d, c);
                    queue.add(s);
                }
            }
        }
        SearchNode solution = current;
        sol.add(solution.getNode().getId());
        while (solution.getPrev() != null) {
            sol.addFirst(solution.getPrev().getNode().getId());
            solution = solution.getPrev();
        }
        return sol;
    }


    public static double distance(double lon1, double lat1, double lon2, double lat2) {
        return Math.sqrt(Math.pow(Math.abs(lon2 - lon1), 2) + Math.pow(Math.abs(lat2 - lat1), 2));
    }

}
