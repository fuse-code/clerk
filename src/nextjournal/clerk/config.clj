(ns nextjournal.clerk.config
  (:require [clojure.string :as str]))

(defn cache-dir []
  (or (System/getProperty "clerk.cache_dir")
      ".cache"))

(defn cache-disabled? []
  (when-let [prop (System/getProperty "clerk.disable_cache")]
    (not= "false" prop)))

(def default-resource-manifest
  {"/js/viewer.js" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VufNXFDYymQD5T5zdSrX4tPbAHKpGQ3wCeTffp5Tot9yuYfLz98AU3DHW3QFWPNyteKUqHFPieLVR1fojL1hLrW8e"})

(def resource-manifest-from-props
  (when-let [prop (System/getProperty "clerk.resource_manifest")]
    (when-not (str/blank? prop)
      (read-string prop))))

(defonce !resource->url
  (atom (or resource-manifest-from-props
            default-resource-manifest)))

#_(swap! !resource->url assoc "/css/viewer.css" "https://storage.googleapis.com/nextjournal-cas-eu/data/8VvAV62HzsvhcsXEkHP33uj4cV9UvdDz7DU9qLeVRCfEP9kWLFAzaMKL77trdx898DzcVyDVejdfxvxj5XB84UpWvQ")
#_(swap! !resource->url dissoc "/css/viewer.css")
#_(reset! !resource->url identity)
#_(reset! !resource->url default-resource-manifest)


(def ^:dynamic *in-clerk* false)

(def ^:dynamic *bounded-count-limit*
  (or (let [limit (System/getProperty "clerk.bounded-count-limit")]
        (try
          (some-> limit not-empty Integer/parseInt)
          (catch Exception _
            (throw (ex-info "Invalid value for property `clerk.bounded-count-limit`, must be integer." {:value limit})))))
      1000000))
