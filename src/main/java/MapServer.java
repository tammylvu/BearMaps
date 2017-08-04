import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.Collections;
import java.util.ArrayList;

/* Maven is used to pull in these dependencies. */
import com.google.gson.Gson;

import javax.imageio.ImageIO;

import static spark.Spark.*;

/**
 * This MapServer class is the entry point for running the JavaSpark web server for the BearMaps
 * application project, receiving API calls, handling the API call processing, and generating
 * requested images and routes.
 *
 * @author Alan Yao
 */
public class MapServer {
    /**
     * The root upper left/lower right longitudes and latitudes represent the bounding box of
     * the root tile, as the images in the img/ folder are scraped.
     * Longitude == x-axis; latitude == y-axis.
     */
    public static final double ROOT_ULLAT = 37.892195547244356, ROOT_ULLON = -122.2998046875,
            ROOT_LRLAT = 37.82280243352756, ROOT_LRLON = -122.2119140625;
    /**
     * Each tile is 256x256 pixels.
     */
    public static final int TILE_SIZE = 256;
    /**
     * HTTP failed response.
     */
    private static final int HALT_RESPONSE = 403;
    /**
     * Route stroke information: typically roads are not more than 5px wide.
     */
    public static final float ROUTE_STROKE_WIDTH_PX = 5.0f;
    /**
     * Route stroke information: Cyan with half transparency.
     */
    public static final Color ROUTE_STROKE_COLOR = new Color(108, 181, 230, 200);
    /**
     * The tile images are in the IMG_ROOT folder.
     */
    private static final String IMG_ROOT = "img/";
    /**
     * The OSM XML file path. Downloaded from <a href="http://download.bbbike.org/osm/">here</a>
     * using custom region selection.
     **/
    private static final String OSM_DB_PATH = "berkeley.osm";

    private static final String[]
            REQUIRED_RASTER_REQUEST_PARAMS = {"ullat", "ullon", "lrlat", "lrlon", "w", "h"};
    private static final String[]
            REQUIRED_ROUTE_REQUEST_PARAMS = {"start_lat", "start_lon", "end_lat", "end_lon"};
    /* Define any static variables here. Do not define any instance variables of MapServer. */
    private static GraphDB g;
    private static QuadTree root;
    private static LinkedList<Long> l;

    /**
     * Place any initialization statements that will be run before the server main loop here.
     * Do not place it in the main function. Do not place initialization code anywhere else.
     * This is for testing purposes, and you may fail tests otherwise.
     **/
    public static void initialize() {
        g = new GraphDB(OSM_DB_PATH);
        root = new QuadTree(ROOT_ULLON, ROOT_ULLAT, ROOT_LRLON, ROOT_LRLAT);
    }

    public static void main(String[] args) {
        initialize();
        staticFileLocation("/page");
        /* Allow for all origin requests (since this is not an authenticated server, we do not
         * care about CSRF).  */
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
        });

        /* Define the raster endpoint for HTTP GET requests. I use anonymous functions to define
         * the request handlers. */
        get("/raster", (req, res) -> {
            HashMap<String, Double> params =
                    getRequestParams(req, REQUIRED_RASTER_REQUEST_PARAMS);
            /* The png image is written to the ByteArrayOutputStream */
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            /* getMapRaster() does almost all the work for this API call */
            Map<String, Object> rasteredImgParams = getMapRaster(params, os);
            /* On an image query success, add the image data to the response */
            if (rasteredImgParams.containsKey("query_success")
                    && (Boolean) rasteredImgParams.get("query_success")) {
                String encodedImage = Base64.getEncoder().encodeToString(os.toByteArray());
                rasteredImgParams.put("b64_encoded_image_data", encodedImage);
            }
            /* Encode response to Json */
            Gson gson = new Gson();
            return gson.toJson(rasteredImgParams);
        });

        /* Define the routing endpoint for HTTP GET requests. */
        get("/route", (req, res) -> {
            HashMap<String, Double> params =
                    getRequestParams(req, REQUIRED_ROUTE_REQUEST_PARAMS);
            LinkedList<Long> route = findAndSetRoute(params);
            return !route.isEmpty();
        });

        /* Define the API endpoint for clearing the current route. */
        get("/clear_route", (req, res) -> {
            clearRoute();
            return true;
        });

        /* Define the API endpoint for search */
        get("/search", (req, res) -> {
            Set<String> reqParams = req.queryParams();
            String term = req.queryParams("term");
            Gson gson = new Gson();
            /* Search for actual location data. */
            if (reqParams.contains("full")) {
                List<Map<String, Object>> data = getLocations(term);
                return gson.toJson(data);
            } else {
                /* Search for prefix matching strings. */
                List<String> matches = getLocationsByPrefix(term);
                return gson.toJson(matches);
            }
        });

        /* Define map application redirect */
        get("/", (request, response) -> {
            response.redirect("/map.html", 301);
            return true;
        });
    }

    /**
     * Validate & return a parameter map of the required request parameters.
     * Requires that all input parameters are doubles.
     *
     * @param req            HTTP Request
     * @param requiredParams TestParams to validate
     * @return A populated map of input parameter to it's numerical value.
     */
    private static HashMap<String, Double> getRequestParams(
            spark.Request req, String[] requiredParams) {
        Set<String> reqParams = req.queryParams();
        HashMap<String, Double> params = new HashMap<>();
        for (String param : requiredParams) {
            if (!reqParams.contains(param)) {
                halt(HALT_RESPONSE, "Request failed - parameters missing.");
            } else {
                try {
                    params.put(param, Double.parseDouble(req.queryParams(param)));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    halt(HALT_RESPONSE, "Incorrect parameters - provide numbers.");
                }
            }
        }
        return params;
    }


    /**
     * Handles raster API calls, queries for tiles and rasters the full image. <br>
     * <p>
     * The rastered photo must have the following properties:
     * <ul>
     * <li>Has dimensions of at least w by h, where w and h are the user viewport width
     * and height.</li>
     * <li>The tiles collected must cover the most longitudinal distance per pixel
     * possible, while still covering less than or equal to the amount of
     * longitudinal distance per pixel in the query box for the user viewport size. </li>
     * <li>Contains all tiles that intersect the query bounding box that fulfill the
     * above condition.</li>
     * <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * <li>If a current route exists, lines of width ROUTE_STROKE_WIDTH_PX and of color
     * ROUTE_STROKE_COLOR are drawn between all nodes on the route in the rastered photo.
     * </li>
     * </ul>
     * Additional image about the raster is returned and is to be included in the Json response.
     * </p>
     *
     * @param params Map of the HTTP GET request's query parameters - the query bounding box and
     *               the user viewport width and height.
     * @param os     An OutputStream that the resulting png image should be written to.
     * @return A map of parameters for the Json response as specified:
     * "raster_ul_lon" -> Double, the bounding upper left longitude of the rastered image <br>
     * "raster_ul_lat" -> Double, the bounding upper left latitude of the rastered image <br>
     * "raster_lr_lon" -> Double, the bounding lower right longitude of the rastered image <br>
     * "raster_lr_lat" -> Double, the bounding lower right latitude of the rastered image <br>
     * "raster_width"  -> Double, the width of the rastered image <br>
     * "raster_height" -> Double, the height of the rastered image <br>
     * "depth"         -> Double, the 1-indexed quadtree depth of the nodes of the rastered image.
     * Can also be interpreted as the length of the numbers in the image string. <br>
     * "query_success" -> Boolean, whether an image was successfully rastered. <br>
     * @see #REQUIRED_RASTER_REQUEST_PARAMS
     */

    public static Map<String, Object> getMapRaster(Map<String, Double> params, OutputStream os) {
        HashMap<String, Object> rasteredImageParams = new HashMap<>();
        ArrayList<QTreeNode> a = root.intersects(params.get("ullon"), params.get("ullat"),
                params.get("lrlon"), params.get("lrlat"), params.get("w"));
        Collections.sort(a);
        QTreeNode first = a.get(0);
        QTreeNode last = a.get(a.size() - 1);
        double width = (first.getUllon() - last.getLrlon())
                / (first.getUllon() - first.getLrlon());
        double height = (first.getUllat() - last.getLrlat())
                / (first.getUllat() - first.getLrlat());
        int imageWidth = (int) Math.abs(Math.round(width * 256));
        int imageHeight = (int) Math.abs(Math.round(height * 256));
        double rasterLrLon = a.get(a.size() - 1).getLrlon();
        double rasterLrLat = a.get(a.size() - 1).getLrlat();
        double rasterUlLon = a.get(0).getUllon();
        double rasterUlLat = a.get(0).getUllat();

        BufferedImage result = new BufferedImage(imageWidth, imageHeight,
                BufferedImage.TYPE_INT_RGB);
        Graphics graphics1 = result.getGraphics();
        int x = 0;
        int y = 0;
        try {
            for (QTreeNode node : a) {
                String image = Integer.toString(node.getNumber()) + ".png";
                BufferedImage bi = ImageIO.read(new File(IMG_ROOT + image));
                graphics1.drawImage(bi, x, y, null);
                x += TILE_SIZE;

                if (x >= result.getWidth()) {
                    x = 0;
                    y += TILE_SIZE;
                }
            }
            if (l != null && l.size() != 0) {
                for (int i = 0; i < l.size() - 1; i++) {
                    Node current = g.getNodes().get(l.get(i));
                    Node next = g.getNodes().get(l.get(i + 1));
                    double v = (rasterLrLon - rasterUlLon) / imageWidth;
                    double w = (rasterUlLat - rasterLrLat) / imageHeight;
                    int currX = (int) ((current.getLon() - rasterUlLon) / v);
                    int currY = (int) ((rasterUlLat - current.getLat()) / w);
                    int nextX = (int) ((next.getLon() - rasterUlLon) / v);
                    int nextY = (int) ((rasterUlLat - next.getLat()) / w);
                    Graphics2D graphics2 = (Graphics2D) graphics1;
                    graphics2.setColor(ROUTE_STROKE_COLOR);
                    graphics2.setStroke(new BasicStroke(ROUTE_STROKE_WIDTH_PX,
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    graphics2.drawLine(currX, currY, nextX, nextY);
                }
            }
            ImageIO.write(result, "png", os);
        } catch (IOException e) {
            System.out.println("BOO");
        }

        rasteredImageParams.put("raster_ul_lon", a.get(0).getUllon());
        rasteredImageParams.put("raster_ul_lat", a.get(0).getUllat());
        rasteredImageParams.put("raster_lr_lon", a.get(a.size() - 1).getLrlon());
        rasteredImageParams.put("raster_lr_lat", a.get(a.size() - 1).getLrlat());
        rasteredImageParams.put("raster_width", imageWidth);
        rasteredImageParams.put("raster_height", imageHeight);
        rasteredImageParams.put("depth", a.get(0).getDepth());
        rasteredImageParams.put("query_success", true);
        return rasteredImageParams;
    }

    /**
     * Searches for the shortest route satisfying the input request parameters, sets it to be the
     * current route, and returns a <code>LinkedList</code> of the route's node ids for testing
     * purposes. <br>
     * The route should start from the closest node to the start point and end at the closest node
     * to the endpoint. Distance is defined as the euclidean between two points (lon1, lat1) and
     * (lon2, lat2).
     *
     * @param params from the API call described in REQUIRED_ROUTE_REQUEST_PARAMS
     * @return A LinkedList of node ids from the start of the route to the end.
     */
    public static LinkedList<Long> findAndSetRoute(Map<String, Double> params) {
        double startlat = params.get("start_lat");
        double startlon = params.get("start_lon");
        double endlat = params.get("end_lat");
        double endlon = params.get("end_lon");
        HashMap<Long, Node> nodes = g.getNodes();
        Node closestStart = null;
        Node closestEnd = null;
        double minDistanceS = Double.MAX_VALUE;
        double minDistanceE = Double.MAX_VALUE;
        for (Node n : nodes.values()) {
            double distFromStart = distance(n.getLon(), n.getLat(), startlon, startlat);
            double distFromEnd = distance(n.getLon(), n.getLat(), endlon, endlat);
            if (distFromStart < minDistanceS) {
                minDistanceS = distFromStart;
                closestStart = n;
            }
            if (distFromEnd < minDistanceE) {
                minDistanceE = distFromEnd;
                closestEnd = n;
            }
        }
        Aalgorithm pathFinder = new Aalgorithm(closestStart, closestEnd);
        LinkedList<Long> list = pathFinder.findPath();
        for (Node n : g.getNodes().values()) {
            n.setMark(false);
        }
        l = list;
        return list;
    }

    public static double distance(double lon1, double lat1, double lon2, double lat2) {
        return Math.sqrt(Math.pow(lon2 - lon1, 2) + Math.pow(lat2 - lat1, 2));
    }

    /**
     * Clear the current found route, if it exists.
     */
    public static void clearRoute() {

    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     *
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public static List<String> getLocationsByPrefix(String prefix) {
        return new LinkedList<>();
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     *
     * @param locationName A full name of a location searched for.
     * @return A list of locations whose cleaned name matches the
     * cleaned <code>locationName</code>, and each location is a map of parameters for the Json
     * response as specified: <br>
     * "lat" -> Number, The latitude of the node. <br>
     * "lon" -> Number, The longitude of the node. <br>
     * "name" -> String, The actual name of the node. <br>
     * "id" -> Number, The id of the node. <br>
     */
    public static List<Map<String, Object>> getLocations(String locationName) {
        return new LinkedList<>();
    }
}
