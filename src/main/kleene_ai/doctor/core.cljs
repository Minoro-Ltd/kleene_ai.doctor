(ns kleene-ai.doctor.core
  (:require ["@tryfabric/martian" :refer (markdownToBlocks markdownToRichText)]
            [cljs-node-io.core :as io]
            [clojure.string :as str]
            [promesa.core :as p]
            ["fs" :as fs]))

(def token "")

(def notion-version "2022-02-22")


(def headers {:Content-Type "application/json"
               :Authorization token
               :Notion-Version notion-version})

(defn json-str->clj [s]
  (-> (js/JSON.parse s)
      js->clj))


(defn fetch [url options]
  (let [https? (str/starts-with? url "https")
        http   (js/require (str "http" (when https? "s")))]
    (js/Promise.
     (fn [resolve reject]
       (let [req (.request
                  http
                  url
                  (clj->js options)
                  (fn [res]
                    (let [body (atom "")]
                      (.on res "data" #(swap! body str %))
                      (.on res "error" reject)
                      (.on res "end" #(resolve @body)))))]
         (.write req (or (:body options) ""))
         (.end req))))))


(defn search [name]
  (p/let [body (js/JSON.stringify (clj->js {:query (str name)
                                            :filter {:value "page"
                                                     :property "object"}}))]
    (fetch "https://api.notion.com/v1/search"
           {:method "POST"
            :body body
            :headers headers})))


(defn extract-title [m]
  (get-in m ["properties" "title" "title" 0  "plain_text"]))


(defn get-page-data [name]
  (p/let [body (js/JSON.stringify (clj->js {:query (str name)
                                            :filter {:value "page"
                                                     :property "object"}}))
          response (search name)
          clj-response (json-str->clj response)
          results (get clj-response "results")
          selected-page (first (filter #(= (extract-title %) name) results))
          title (extract-title selected-page)
          parent-id (get-in selected-page ["parent" "page_id"])
          id  (get-in selected-page ["id"])]

    {:title title
     :parent-id parent-id
     :id id}))


(defn post-page [page]
  (let [body (js/JSON.stringify (clj->js page))]
    (fetch "https://api.notion.com/v1/pages"
           {:method "POST"
            :body body
            :headers headers})))



;; make this use pagination using iteration :)

(defn get-all-block-ids [page-id]
  (p/let [response (fetch (str "https://api.notion.com/v1/blocks/" page-id "/children")
                          {:method "GET"
                           :body ""
                           :headers headers})
          results  (-> (json-str->clj response)
                       (get-in ["results"]))]
    (map #(get % "id") results)))


(defn remove-blocks-from-page! [page-id]
  (p/let [block-ids (get-all-block-ids page-id)]
    (p/run! #(fetch (str "https://api.notion.com/v1/blocks/" %) {:method "DELETE"
                                                                 :headers headers})
         block-ids)))


(defn add-blocks-to-page! [page-id blocks]
  (p/let [body     (js/JSON.stringify (clj->js {:children blocks}))
          response (fetch (str "https://api.notion.com/v1/blocks/" page-id "/children")
                          {:method "PATCH"
                           :body body
                           :headers headers})]
    (prn "added blocks to page")))


(defn title-block [name]
  {:id "title",
   :type "title",
   :title
   [{:type "text",
     :text {:content name, :link nil},
     :annotations
     {:bold false,
      :italic false,
      :strikethrough false,
      :underline false,
      :code false,
      :color "default"},
     :plain_text name,
     :href nil}]})


(defn page-block [parent-id title content]
  {:properties {:title title},
   :parent {:type "page_id", :page_id parent-id},
   :children content})


(defn create-page! [parent-id title md-content]
  (p/let [content (markdownToBlocks md-content)
          title (title-block title)
          page (page-block parent-id title content)
          response (post-page page)
          id (-> (json-str->clj response)
                 (get "id"))]
    {:title title :id id}))


(defn update-page! [page-id md-content]
  (p/let [removing-blocks (remove-blocks-from-page! page-id)
          adding-blocks (add-blocks-to-page! page-id (markdownToBlocks md-content))]
   page-id))


(defn is-directory? [path]
  (.isDirectory (fs/lstatSync path)))


(defn get-all-files [path]
  (->> (fs/readdirSync path #js{:withFileTypes true})
       (remove #(.isDirectory %))
       (map #(.-name %))))


(defn get-all-dirs [path]
  (->> (fs/readdirSync path #js{:withFileTypes true})
       (filter #(.isDirectory %))
       (map #(.-name %))))


(defn create-or-update-page! [parent-id path filename]
  (p/let [title (first (str/split filename #"\."))
          results (get-page-data title)
          id (:id results)
          md-content (io/slurp (str path "/" filename))]
    (if id
      (update-page! (:id results) md-content)
      (create-page! parent-id title md-content))))


(defn create-or-update-directory! [parent-id filename]
  (p/let [results (get-page-data filename)
          id (:id results)]
    (if id
      {:id id :title filename}
      (create-page! parent-id filename  ""))))


(defn create-all-in-dir! [parent-id path]
  (p/let [_ (prn parent-id path)
          files (get-all-files path)
          file-results (p/all (mapv #(create-or-update-page! parent-id path %) files))
          directories (get-all-dirs path)
          dir-results(p/all (mapv #(create-or-update-directory! parent-id %) directories))
          _ (prn dir-results)]
    (if (not-empty directories)
      (p/run! (fn [{:keys [title id]}] (create-all-in-dir! id (str path "/" title))) dir-results)
      (prn (str "done with: " path)))))


(defn main [& args]
  )


(comment
  (.then (create-all-in-dir! doctor-id "docs") #(prn "done"))


  (def doctor-id "")

  (create-or-update-page doctor-id "Example1" "docs/ExampleProj2/README.md")

  (p/let [page-data (get-page-data "DocTor")
          {:keys [id]} page-data
          content (io/slurp "docs/ExampleProj2/README.md")]
    (create-page! id "Example1" content))

  (get-all-dirs "docs")
  (get-all-files "docs")

  (.isDirectory (fs/statSync "docs"))
  (is-directory? "docs/Jumper")

  (io/file-seq "docs")

  (io/slurp "docs")

  (js/JSON.stringify
   (markdownToBlocks ex))

  (js/JSON.stringify (markdownToRichText ex))

  (defn get-all-dirs [path]
    (let [paths (io/file-seq path)]
      (filter #(and (is-directory? %)
                    (not= "docs" %)) paths)))

  (defn get-all-files [path]
    (let [paths (io/file-seq path)]
      (remove is-directory? paths))))
