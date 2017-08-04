import java.util.ArrayList;

/**
 * Created by Tam on 4/7/2016.
 */

public class QuadTree {
    private QTreeNode root;

    public QuadTree(double ullon, double ullat, double lrlon, double lrlat) {
        root = new QTreeNode(ullon, ullat, lrlon, lrlat, 0, 0);
    }

    public QTreeNode getRoot() {
        return root;
    }

    public int findDepth(double qDPP) {
        QTreeNode current = root;
        double dpp = (current.getLrlon() - current.getUllon()) / 256.0;
        while (qDPP <= dpp) {
            if (current.getDepth() == 7) {
                break;
            }
            current = current.getChildren().get(0);
            dpp = (current.getLrlon() - current.getUllon()) / 256.0;
        }
        return current.getDepth();
    }

    public void getDepthNodes(int target, QTreeNode node, ArrayList<QTreeNode> list) {
        if (target < node.getDepth()) {
            return;
        } else if (node.getDepth() == target) {
            list.add(node);
        } else {
            for (QTreeNode tile: node.getChildren()) {
                getDepthNodes(target, tile, list);
            }
        }
    }

    public ArrayList<QTreeNode> intersects(double ullon, double ullat,
                                           double lrlon, double lrlat, double w) {
        double qdpp = Math.abs(lrlon - ullon) / w;
        ArrayList<QTreeNode> al = new ArrayList<>();
        ArrayList<QTreeNode> list = new ArrayList<>();
        getDepthNodes(findDepth(qdpp), root, al);
        for (QTreeNode tile: al) {
            if (ullon < tile.getLrlon()
                    && ullat > tile.getLrlat()
                    && lrlon > tile.getUllon()
                    && lrlat < tile.getUllat()) {
                list.add(tile);
            }
        }
        return list;
    }

    public static void main(String[] args) {
        QuadTree rootNode = new QuadTree(-122.2998046875, 37.892195547244356, -122.2119140625, 37.82280243352756);
        int depth = rootNode.findDepth(1.23542 * Math.pow(10, -6));
        //System.out.println(depth);
        ArrayList<QTreeNode> finalNodes = new ArrayList<>();
        finalNodes = rootNode.intersects(-122.241632, 37.87655, -122.24053, 37.87548, 892);
        System.out.print(rootNode.findDepth(.00005));
        //System.out.println(finalNodes);
        for (QTreeNode x : finalNodes) {
            //System.out.println("!");
            //System.out.println(x.getDepth());
        }
    }

}
