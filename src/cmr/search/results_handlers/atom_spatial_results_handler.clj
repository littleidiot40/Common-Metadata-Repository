(ns cmr.search.results-handlers.atom-spatial-results-handler
  "A helper for converting spatial shapes into ATOM results"
  (:require [clojure.data.xml :as x]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.ring :as r]
            [cmr.spatial.line :as l]
            [clojure.string :as str]))

(defn- points-map->points-str
  "Converts a map containing :points into the lat lon space separated points string of atom"
  [{:keys [points]}]
  (str/join " " (mapcat #(vector (:lat %) (:lon %)) points)))


(defprotocol JsonSpatialHandler
  (shape->string
    [shape]
    "Converts a spatial shape into the string of ordinates"))

(extend-protocol JsonSpatialHandler

  cmr.spatial.point.Point
  (shape->string
    [{:keys [lon lat]}]
    (str lat " " lon))

  cmr.spatial.line.Line
  (shape->string
    [line]
    (points-map->points-str line))

  cmr.spatial.mbr.Mbr
  (shape->string
    [{:keys [west north east south]}]
    (str/join " " [south west north east]))

  cmr.spatial.ring.Ring
  (shape->string
    [ring]
    (points-map->points-str ring)))

(defn- polygon->json
  "Returns the json representation of the given polygon"
  [{:keys [rings]}]
  (if (= (count rings) 1)
    [(shape->string (first rings))]
    (let [boundary (first rings)
          holes (rest rings)]
      (concat [(shape->string boundary)] (map shape->string holes)))))

(defn shapes->json
  "Returns the json representation of the given shapes"
  [shapes]
  (let [shapes-by-type (group-by type shapes)
        points (map shape->string (get shapes-by-type cmr.spatial.point.Point))
        boxes (map shape->string (get shapes-by-type cmr.spatial.mbr.Mbr))
        polygons (map polygon->json (get shapes-by-type cmr.spatial.polygon.Polygon))
        lines (map shape->string (get shapes-by-type cmr.spatial.line.Line))
        spatial {:points points
                 :boxes boxes
                 :polygons polygons
                 :lines lines}]
    (apply dissoc
           spatial
           (for [[k v] spatial :when (empty? v)] k))))


(defprotocol AtomSpatialHandler
  (shape->xml-element
    [shape]
    "Converts a spatial shape into the ATOM XML element"))

(extend-protocol AtomSpatialHandler

  cmr.spatial.point.Point
  (shape->xml-element
    [point]
    (x/element :georss:point {} (shape->string point)))

  cmr.spatial.line.Line
  (shape->xml-element
    [line]
    (x/element :georss:line {} (shape->string line)))

  cmr.spatial.mbr.Mbr
  (shape->xml-element
    [mbr]
    (x/element :georss:box {} (shape->string mbr)))

  cmr.spatial.ring.Ring
  (shape->xml-element
    [ring]
    (x/element :gml:LinearRing {}
               (x/element :gml:posList {}
                          (shape->string ring))))

  cmr.spatial.polygon.Polygon
  (shape->xml-element
    [{:keys [rings]}]
    (if (= (count rings) 1)
      (x/element :georss:polygon {} (shape->string (first rings)))
      (let [boundary (first rings)
            holes (rest rings)]
        (x/element :georss:where {}
                   (x/element :gml:Polygon {}
                              (x/element :gml:exterior {} (shape->xml-element boundary))
                              (x/element :gml:interior {} (map shape->xml-element holes))))))))

