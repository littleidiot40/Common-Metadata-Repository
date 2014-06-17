(ns cmr.spatial.test.lr-binary-search
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :as ext-gen :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.math.combinatorics :as combo]
            [clojure.string :as str]

            ;; my code
            [cmr.spatial.math :refer :all]
            [cmr.spatial.point :as p]
            [cmr.spatial.arc :as a]
            [cmr.spatial.ring :as r]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.derived :as d]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.test.generators :as sgen]
            [cmr.spatial.lr-binary-search :as lbs]
            [cmr.spatial.relations :as relations]
            [cmr.spatial.dev.viz-helper :as viz-helper])
  (:import cmr.spatial.point.Point))

(defspec all-rings-have-lrs {:times 100 :printer-fn sgen/print-failed-ring}
  (for-all [ring (sgen/rings)]
    (let [lr (lbs/find-lr ring false)]
      (and lr
           (r/covers-br? ring lr)))))

(defspec simple-polygon-with-holes-has-lr {:times 100 :printer-fn sgen/print-failed-ring}
  (let [boundary (d/calculate-derived (r/ords->ring 0 0, 10 0, 10 10, 0 10, 0 0))]
    (for-all [hole (sgen/rings-in-ring boundary)]
      (let [polygon (d/calculate-derived (poly/polygon [boundary hole]))
            lr (lbs/find-lr polygon false)]
        (and lr
             (poly/covers-br? polygon lr))))))

(defspec all-polygons-with-holes-have-lrs {:times 100 :printer-fn sgen/print-failed-polygon}
  (for-all [polygon (gen/no-shrink sgen/polygons-with-holes)]
    (let [lr (lbs/find-lr polygon false)]
      (and lr
           (poly/covers-br? polygon lr)))))
(comment
  ;; Visualization samples and helpers

  (display-draggable-lr-polygon
    (poly/polygon [(r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0)
                   (r/ords->ring 4,3.34, 2,3.34, 3,1.67, 4,3.34)]))

  ;; TODO fix this problem where if the search point is in the bottom corner of the polygon
  ;; then an LR will be found that has no height and it could be taller

  (let [ordses [[0.0 0.0 6.68 0.65 5.54 5.37 0.07 4.5 0.0 0.0]
                [3.4 4.57 0.68 4.14 0.55 0.27 3.4 4.57]]
        rings (map (partial apply r/ords->ring) ordses)
        polygon (d/calculate-derived (poly/polygon rings))]
    (display-draggable-lr-polygon polygon)
    (lbs/find-lr polygon))



  (def ring (d/calculate-derived
              (first (gen/sample (sgen/rings) 1))))

  ;; Samples

  ;; Normal
  (display-draggable-lr-ring
    (r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0))

  ;; Very larges
  (display-draggable-lr-ring
    (r/ords->ring -89.9 -45, 89.9 -45, 89.9 45, -89.9 45, -89 -45))

  ;; around north pole
  (display-draggable-lr-ring
    (r/ords->ring 45 85, 90 85, 135 85, 180 85, -135 85, -45 85, 45 85))

  ;; around south pole
  (display-draggable-lr-ring
    (r/ords->ring 45 -85, -45 -85, -135 -85, 180 -85, 135 -85, 90 -85, 45 -85))

  ;; across antimeridian
  (display-draggable-lr-ring
    (r/ords->ring 175 -10, -175 -10, -175 0, -175 10
                  175 10, 175 0, 175 -10))

  ;; Performance testing
  (require '[criterium.core :refer [with-progress-reporting bench]])

  (let [ring (d/calculate-derived (r/ords->ring 0,0, 4,0, 6,5, 2,5, 0,0))]
    (with-progress-reporting
      (bench
        (lbs/find-lr ring))))

  (defn br->ring-with-n-points
    "Creates a ring with at least n points from the br. Br must not cross antimeridian"
    [br ^double n]
    (let [{:keys [^double west ^double north ^double east ^double south]} br
          num-edge (/ n 2.0)
          edge-length (/ (- east west) num-edge)
          south-points (for [i (range num-edge)]
                         (p/point (+ west (* i edge-length)) south))
          north-points (for [i (range num-edge)]
                         (p/point (- east (* i edge-length)) north))]

      (r/ring (concat south-points north-points [(first south-points)]))))

  (defn measure-find-lr-performance
    [n-points]
    (let [ring (d/calculate-derived (br->ring-with-n-points (m/mbr -170 45 170 -45) n-points))]
      (with-progress-reporting
        (bench
          (lbs/find-lr ring)))))

  (measure-find-lr-performance 4) ; 1.4 ms
  (measure-find-lr-performance 50) ; 5 ms
  (measure-find-lr-performance 2000) ; 301 ms

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for visualizing and interacting with polygons to show LRs

(def displaying-polygon-atom
  "Holds onto the current polygon being displayed. A single ring is sent back from the visualizztion
  when it is dragged. We update this polygon and use it to find the current LR."
  (atom nil))

(comment
  (mapv r/ring->ords (:rings @displaying-polygon-atom))

  )

(defn display-draggable-lr-polygon
  "Displays a draggable polygon in the spatial visualization. As the polygon is dragged the LR of the polygon is updated."
  [polygon]
  (viz-helper/clear-geometries)
  (let [polygon (d/calculate-derived polygon)
        lr (lbs/find-lr polygon false)
        callback "cmr.spatial.test.lr-binary-search/handle-polygon-moved"
        ;; Add options for polygon to be displayed.
        polygon (-> polygon
                    (assoc :options {:callbackFn callback
                                     :draggable true})
                    (update-in [:rings] (fn [rings]
                                          (vec (map-indexed
                                                 (fn [i ring]
                                                   (assoc-in ring [:options :id] i))
                                                 rings)))))
        ;; Find the points used to find the LR. We'll display these to help debug the algorithm
        lr-search-points (map #(assoc-in % [:options :id] "search-point")
                              (lbs/shape->lr-search-points polygon))
        _ (println "Found LR:" (pr-str lr))]

    (reset! displaying-polygon-atom polygon)
    (viz-helper/add-geometries (concat [polygon]
                                       lr-search-points))
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

(defn handle-polygon-moved
  "Callback handler for when the polygon is moved. It removes the existing polygon and lr and readds it with
  the updated lr."
  [ring-str]
  (let [[^String id ords-str] (str/split ring-str #":")
        ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (d/calculate-derived (apply r/ords->ring ords))
        polygon (swap! displaying-polygon-atom (fn [polygon]
                                                 (assoc-in polygon [:rings (Long. id)] ring)))
        ;; Find the points used to find the LR. We'll display these to help debug the algorithm
        lr-search-points (map #(assoc-in % [:options :id] "search-point")
                              (lbs/shape->lr-search-points polygon))
        lr (lbs/find-lr polygon false)
        _ (println "Found LR:" (pr-str lr))]
    (viz-helper/remove-geometries ["lr" "search-point"])
    (println "lr-search-points" (pr-str lr-search-points))
    (viz-helper/add-geometries lr-search-points)
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions for visualizing and interacting with rings to show LRs

(defn display-draggable-lr-ring
  "Displays a draggable ring in the spatial visualization. As the ring is dragged the LR of the ring is updated."
  [ring]
  (viz-helper/clear-geometries)
  (let [ring (d/calculate-derived ring)
        lr (lbs/find-lr ring false)
        callback "cmr.spatial.test.lr-binary-search/handle-ring-moved"
        ring (assoc ring
                    :options {:callbackFn callback
                              :draggable true})
        _ (println "Found LR:" (pr-str lr))]
    (viz-helper/add-geometries [ring])
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))

(defn handle-ring-moved
  "Callback handler for when the ring is moved. It removes the existing ring and lr and readds it with
  the updated lr."
  [ords-str]
  (let [ords (map #(Double. ^String %) (str/split ords-str #","))
        ring (d/calculate-derived (apply r/ords->ring ords))
        lr (lbs/find-lr ring false)]
    (viz-helper/remove-geometries ["lr"])
    (when lr
      (viz-helper/add-geometries [(assoc-in lr [:options :id] "lr")]))))



