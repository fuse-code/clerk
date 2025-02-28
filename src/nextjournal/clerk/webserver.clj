(ns nextjournal.clerk.webserver
  (:require [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.edn :as edn]
            [org.httpkit.server :as httpkit]
            [nextjournal.clerk.view :as view]
            [nextjournal.clerk.viewer :as v]
            [lambdaisland.uri :as uri]))

(def help-doc
  [{:type :markdown :text "Use `nextjournal.clerk/show!` to make your notebook appear…"}])

(defonce !clients (atom #{}))
(defonce !doc (atom help-doc))
(defonce !error (atom nil))


#_(view/doc->viewer @!doc)
#_(meta @!doc)
#_(reset! !doc help-doc)

(defn broadcast! [msg]
  (doseq [ch @!clients]
    (httpkit/send! ch (view/->edn msg))))

#_(broadcast! [{:random (rand-int 10000) :range (range 100)}])

(defn update-if [m k f]
  (if (k m)
    (update m k f)
    m))

#_(update-if {:n "42"} :n #(Integer/parseInt %))

(defn get-fetch-opts [query-string]
  (-> query-string
      uri/query-string->map
      (update-if :n #(Integer/parseInt %))
      (update-if :offset #(Integer/parseInt %))
      (update-if :path #(edn/read-string %))))

#_(get-pagination-opts "")
#_(get-pagination-opts "foo=bar&n=42&start=20")

(defn serve-blob [{:keys [uri query-string]}]
  (let [blob->result (meta @!doc)
        blob-id (str/replace uri "/_blob/" "")
        doc-ns (:ns (meta @!doc))]
    (assert doc-ns "namespace must be set")
    (if (contains? blob->result blob-id)
      (let [result (blob->result blob-id)
            viewers (v/get-viewers doc-ns (v/viewers result))
            opts (assoc (get-fetch-opts query-string) :viewers viewers)
            desc (v/describe result opts)]
        (if (contains? desc :nextjournal/content-type)
          {:body (v/value desc)
           :content-type (:nextjournal/content-type desc)}
          {:body (view/->edn desc)}))
      {:status 404})))



(defn app [{:as req :keys [uri]}]
  (if (:websocket? req)
    (httpkit/as-channel req {:on-open (fn [ch] (swap! !clients conj ch))
                             :on-close (fn [ch _reason] (swap! !clients disj ch))
                             :on-receive (fn [_ch msg] (binding [*ns* (or (:ns (meta @!doc))
                                                                          (create-ns 'user))]
                                                         (eval (read-string msg))))})
    (try
      (case (get (re-matches #"/([^/]*).*" uri) 1)
        "_blob" (serve-blob req)
        "_ws" {:status 200 :body "upgrading..."}
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (view/doc->html @!doc @!error)})
      (catch Throwable e
        {:status  500
         :body    (with-out-str (pprint/pprint (Throwable->map e)))}))))

(defn update-doc! [doc]
  (reset! !error nil)
  (broadcast! {:doc (view/doc->viewer (reset! !doc doc))}))

#_(update-doc! help-doc)

(defn show-error! [e]
  (broadcast! {:error (reset! !error (v/describe e))}))


#_(clojure.java.browse/browse-url "http://localhost:7777")

;; # dynamic requirements
;; * load notebook without results
;; * allow page reload

(defonce !server (atom nil))

(defn halt! []
  (when-let [{:keys [port stop-fn]} @!server]
    (stop-fn)
    (println (str "Webserver running on " port ", stopped."))
    (reset! !server nil)))

(defn serve! [{:keys [port] :or {port 7777}}]
  (halt!)
  (try
    (reset! !server {:port port :stop-fn (httpkit/run-server #'app {:port port})})
    (println (str "Clerk webserver started on " port "..."))
    (catch java.net.BindException _e
      (println "Port " port " not avaible, server not started!"))))

#_(start! {:port 7777})
