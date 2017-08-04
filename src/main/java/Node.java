import java.util.HashSet;

/**
 * Created by Tam on 4/7/2016.
 */
public class Node {
    private long id;
    private double lat;
    private double lon;
    private HashSet<Node> connections;
    private boolean marked = false;
    private String name;
    private int size = 0;

    public Node(long id, double lat, double lon) {
        this.id = id;
        this.lat = lat;
        this.lon = lon;
        connections = new HashSet<>();
        name = "";
    }

    public void setName(String s) {
        name = s;
    }

    public int getSize() {
        return size;
    }

    public long getId() {
        return id;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMark(boolean b) {
        marked = b;
    }

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public void addConnection(Node n) {
        if (!connections.contains(n)) {
            connections.add(n);
        }
        size++;
    }

    public HashSet<Node> getConnections() {
        return connections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Node node = (Node) o;
        if (id != node.id) {
            return false;
        }
        if (Double.compare(node.lat, lat) != 0) {
            return false;
        }
        if (Double.compare(node.lon, lon) != 0) {
            return false;
        }
        if (marked != node.marked) {
            return false;
        }
        return connections.equals(node.connections);

    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}
