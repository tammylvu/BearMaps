import java.util.ArrayList;

/**
 * Created by Tam on 4/7/2016.
 */
public class QTreeNode implements Comparable<QTreeNode> {

    private double ullat;
    private double ullon;
    private double lrlon;
    private double lrlat;
    private double x;
    private double y;
    private int depth;
    private int number;
    private QTreeNode a;
    private QTreeNode b;
    private QTreeNode c;
    private QTreeNode d;

    public QTreeNode(double ullon, double ullat, double lrlon,
                     double lrlat, int depth, int number) {
        this.ullat = ullat;
        this.ullon = ullon;
        this.lrlon = lrlon;
        this.lrlat = lrlat;
        this.depth = depth;
        this.number = number;
        makeChildren();
    }

    public int getDepth() {
        return depth;
    }

    public double getUllat() {
        return ullat;
    }

    public double getUllon() {
        return ullon;
    }

    public double getLrlon() {
        return lrlon;
    }

    public int getNumber() {
        return number;
    }

    public double getLrlat() {
        return lrlat;
    }

    public void makeChildren() {
        if (depth == 7) {
            return;
        }
        x = (lrlon + ullon) / 2.0;
        y = (ullat + lrlat) / 2.0;
        a = new QTreeNode(ullon, ullat, x, y, depth + 1, number * 10 + 1);
        b = new QTreeNode(x, ullat, lrlon, y, depth + 1, number * 10 + 2);
        c = new QTreeNode(ullon, y, x, lrlat, depth + 1, number * 10 + 3);
        d = new QTreeNode(x, y, lrlon, lrlat, depth + 1, number * 10 + 4);
        a.makeChildren();
        b.makeChildren();
        c.makeChildren();
        d.makeChildren();
    }

    public ArrayList<QTreeNode> getChildren() {
        ArrayList<QTreeNode> list = new ArrayList<QTreeNode>();
        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);
        return list;
    }

    @Override
    public int compareTo(QTreeNode other) {
        if (ullat < other.getUllat()) {
            return 1;
        } else if (ullat > other.getUllat()) {
            return -1;
        } else {
            if (ullon > other.getUllon()) {
                return 1;
            } else if (ullon < other.getUllon()) {
                return -1;
            }
        }
        return 0;

    }

}
