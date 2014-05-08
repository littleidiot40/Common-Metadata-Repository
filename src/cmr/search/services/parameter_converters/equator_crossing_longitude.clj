(ns cmr.search.services.parameter-converters.equator-crossing-longitude
  "Contains functions for converting equator-crossing-longitude search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as parser])
  (:import [cmr.search.models.query
            EquatorCrossingLongitudeCondition]
           clojure.lang.ExceptionInfo))

(defn- equator-crossing-longitude-param-str->condition
  [param-str]
  (let [{:keys [value] :as on-map} (parser/numeric-range-parameter->map param-str)]
    (qm/map->EquatorCrossingLongitudeCondition on-map)))

(defn- equator-crossing-longitude-param-map->condition
  [eql-map]
  (try
    (let [numeric-map (into {} (for [[k v] eql-map] [k (Double. v)]))
          {:keys [value]} numeric-map]
      (qm/map->EquatorCrossingLongitudeCondition numeric-map))
    (catch NumberFormatException e
      (errors/internal-error! msg/non-numeric-equator-crossing-longitude-parameter))))


;; Converts orbit-number parameter into a query condition
(defmethod p/parameter->condition :equator-crossing-longitude
  [concept-type param values options]
  (if (string? values)
    (equator-crossing-longitude-param-str->condition values)
    (equator-crossing-longitude-param-map->condition values)))