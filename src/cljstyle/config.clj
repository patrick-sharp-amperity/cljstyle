(ns cljstyle.config
  "Configuration is provided by a map of keyword settings to values.

  Config may be provided by a Clojure file named `.cljstyle`. These files files
  may be sprinkled about the file tree; each file applies configuration to the
  subtree rooted in the directory the file resides in, with deeper files
  merging and overriding their parents."
  (:require
    [clojure.java.io :as io]
    [clojure.spec.alpha :as s])
  (:import
    java.io.File
    java.nio.file.FileSystems))


;; ## Specs

(defn- pattern?
  "True if the value if a regular expression pattern."
  [v]
  (instance? java.util.regex.Pattern v))


;; Formatting Rules
(s/def ::indentation? boolean?)
(s/def ::list-indent-size nat-int?)
(s/def ::line-break-vars? boolean?)
(s/def ::line-break-functions? boolean?)
(s/def ::reformat-types? boolean?)
(s/def ::remove-surrounding-whitespace? boolean?)
(s/def ::remove-trailing-whitespace? boolean?)
(s/def ::insert-missing-whitespace? boolean?)
(s/def ::remove-consecutive-blank-lines? boolean?)
(s/def ::max-consecutive-blank-lines nat-int?)
(s/def ::insert-padding-lines? boolean?)
(s/def ::padding-lines nat-int?)
(s/def ::rewrite-namespaces? boolean?)
(s/def ::single-import-break-width nat-int?)
(s/def ::require-eof-newline? boolean?)


;; Indentation Rules
(s/def ::indent-key
  (s/or :symbol symbol?
        :pattern pattern?))


(s/def ::indenter
  (s/cat :type #{:inner :block :stair}
         :args (s/+ nat-int?)))


(s/def ::indent-rule
  (s/coll-of ::indenter :kind vector?))


(s/def ::indents
  (s/map-of ::indent-key ::indent-rule))


;; File Behavior
(s/def ::file-pattern pattern?)

(s/def ::file-ignore-rule (s/or :string string? :pattern pattern?))
(s/def ::file-ignore (s/coll-of ::file-ignore-rule :kind set?))


;; Config Map
(s/def ::settings
  (s/keys :opt-un [::indentation?
                   ::list-indent-size
                   ::indents
                   ::line-break-vars?
                   ::line-break-functions?
                   ::reformat-types?
                   ::remove-surrounding-whitespace?
                   ::remove-trailing-whitespace?
                   ::insert-missing-whitespace?
                   ::remove-consecutive-blank-lines?
                   ::max-consecutive-blank-lines
                   ::insert-padding-lines?
                   ::padding-lines
                   ::rewrite-namespaces?
                   ::single-import-break-width
                   ::require-eof-newline?
                   ::file-pattern
                   ::file-ignore]))



;; ## Defaults

(def default-indents
  "Default indentation rules included with the library."
  (read-string (slurp (io/resource "cljstyle/indents.clj"))))


(def legacy-config
  {:indentation? true
   :list-indent-size 2
   :indents default-indents
   :line-break-vars? true
   :line-break-functions? true
   :reformat-types? true
   :remove-surrounding-whitespace? true
   :remove-trailing-whitespace? true
   :insert-missing-whitespace? true
   :remove-consecutive-blank-lines? true
   :max-consecutive-blank-lines 2
   :insert-padding-lines? true
   :padding-lines 2
   :rewrite-namespaces? true
   :single-import-break-width 30
   :require-eof-newline? true
   :file-pattern #"\.clj[csx]?$"
   :file-ignore #{}})


(def new-config
  {:files
   {:pattern nil
    :extensions #{".clj" ".cljs" ".cljc" ".cljx"}
    :ignored #{".git" ".hg"}}

   #_#_ ; probably dooesn't belong here
   :options
   {:report? false
    :verbose? false
    :stats-file nil
    :excludes #{}}

   :rules
   {:indentation
    {:enabled? true
     :list-indent 2
     :indents default-indents}

    :whitespace
    {:enabled? true
     :remove-surrounding? true
     :remove-trailing? true
     :insert-missing? true}

    :blank-lines
    {:enabled? true
     :remove-consecutive? true
     :max-consecutive 2
     :insert-padding? true
     :padding-lines 2}

    :eof-newline
    {:enabled? true}

    :vars
    {:enabled? true
     :line-breaks? true}

    :functions
    {:enabled? true
     :line-breaks? true}

    :types
    {:enabled? true}

    :namespaces
    {:enabled? true
     :single-import-break-width 60}}})


(def default-config
  "Default configuration settings."
  legacy-config)


(defn legacy?
  "True if the provided configuration map has legacy properties in it."
  [config]
  (some (partial contains? config) (keys legacy-config)))


(defn translate-legacy
  "Convert a legacy config map into a modern one."
  [config]
  (letfn [(translate
            [cfg old-key new-path]
            (let [v (get cfg old-key)]
              (if (and (some? v) (not= v (get legacy-config old-key)))
                (assoc-in cfg new-path v)
                cfg)))]
    (->
      config

      ;; File matching
      (translate :file-pattern [:files :pattern])
      (translate :file-ignore  [:files :ignored])

      ;; Indentation rule
      (translate :indentation?     [:rules :indentation :enabled?])
      (translate :list-indent-size [:rules :indentation :list-indent])
      (translate :indents          [:rules :indentation :indents])

      ;; Whitespace rule
      (translate :remove-surrounding-whitespace? [:rules :whitespace :remove-surrounding?])
      (translate :remove-trailing-whitespace?    [:rules :whitespace :remove-trailing?])
      (translate :insert-missing-whitespace?     [:rules :whitespace :insert-missing?])

      ;; Blank lines rule
      (translate :remove-consecutive-blank-lines? [:rules :blank-lines :remove-consecutive?])
      (translate :max-consecutive-blank-lines     [:rules :blank-lines :max-consecutive])
      (translate :insert-padding-lines?           [:rules :blank-lines :insert-padding?])
      (translate :padding-lines                   [:rules :blank-lines :padding-lines])

      ;; EOF newline rule
      (translate :require-eof-newline? [:rules :eof-newline :enabled?])

      ;; Vars rule
      (translate :line-break-vars? [:rules :vars :line-breaks?])

      ;; Functions rule
      (translate :line-break-functions? [:rules :functions :line-breaks?])

      ;; Types rule
      (translate :reformat-types? [:rules :types :enabled?])

      ;; Namespaces rule
      (translate :rewrite-namespaces?       [:rules :namespaces :enabled?])
      (translate :single-import-break-width [:rules :namespaces :single-import-break-width])

      ;; Remove legacy keys
      (as-> cfg
        (apply dissoc cfg (keys legacy-config))))))



;; ## Utilities

(defn source-paths
  "Return the sequence of paths the configuration map was merged from."
  [config]
  (::paths (meta config)))


(defn merge-settings
  "Merge configuration maps together."
  ([] nil)
  ([a] a)
  ([a b]
   (letfn [(merge-values
             [x y]
             (cond
               (:replace (meta y)) y
               (:displace (meta x)) y
               (sequential? x) (into x y)
               (set? x) (into x y)
               (map? x) (merge x y)
               :else y))]
     (with-meta
       (merge-with merge-values a b)
       (update (meta a) ::paths (fnil into []) (source-paths b)))))
  ([a b & more]
   (reduce merge-settings a (cons b more))))



;; ## File Utilities

(defn readable?
  "True if the process can read the given `File`."
  [^File file]
  (and file (.canRead file)))


(defn file?
  "True if the given `File` represents a regular file."
  [^File file]
  (and file (.isFile file)))


(defn directory?
  "True if the given `File` represents a directory."
  [^File file]
  (and file (.isDirectory file)))


(defn canonical-dir
  "Return the nearest canonical directory for the path. If path resolves to a
  file, the parent directory is returned."
  ^File
  [path]
  (let [file (-> path io/file .getAbsoluteFile .getCanonicalFile)]
    (if (.isDirectory file)
      file
      (.getParentFile file))))


(defn source-file?
  "True if the file is a recognized source file."
  [config ^File file]
  (and (file? file)
       (readable? file)
       (re-seq (:file-pattern config) (.getName file))))


(defn ignored?
  "True if the file should be ignored."
  [config exclude-globs ^File file]
  (or
    (some
      (fn test-rule
        [rule]
        (cond
          (string? rule)
          (= rule (.getName file))

          (pattern? rule)
          (boolean (re-seq rule (.getCanonicalPath file)))

          :else false))
      (:file-ignore config))
    (some
      (fn test-glob
        [glob]
        (let [path-matcher (.getPathMatcher (FileSystems/getDefault) (str "glob:" glob))]
          (.matches path-matcher (.toPath file))))
      exclude-globs)))



;; ## Configuration Files

(def ^:const file-name
  "Name which indicates a cljstyle configuration file."
  ".cljstyle")


(defn read-config
  "Read a configuration file. Throws an exception if the read fails or the
  contents are not valid configuration settings."
  [^File file]
  (let [path (.getAbsolutePath file)]
    (->
      (try
        (read-string (slurp file))
        (catch Exception ex
          (throw (ex-info (str "Error loading configuration from file: "
                               path "\n" (.getSimpleName (class ex))
                               ": " (.getMessage ex))
                          {:type ::invalid
                           :path path}
                          ex))))
      (as-> config
        (if (s/valid? ::settings config)
          (vary-meta config assoc ::paths [path])
          (throw (ex-info (str "Invalid configuration loaded from file: " path
                               "\n" (s/explain-str ::settings config))
                          {:type ::invalid
                           :path path})))))))


(defn dir-config
  "Return the map of cljstyle configuration from the file in the given directory,
  if it exists and is readable. Returns nil if the configuration is not present
  or is invalid."
  [^File dir]
  (let [file (io/file dir file-name)]
    (when (and (file? file) (readable? file))
      (read-config file))))


(defn find-up
  "Search upwards from a starting path, collecting cljstyle configuration
  files. Returns a sequence of configuration maps read, with shallower paths
  ordered earlier.

  The search will include configuration in the starting path if it is a
  directory, and will terminate after `limit` recursions or once it hits the
  filesystem root or a directory the user can't read."
  [start limit]
  {:pre [start (pos-int? limit)]}
  (loop [configs ()
         dir (canonical-dir start)
         limit limit]
    (if (and (pos? limit) (directory? dir) (readable? dir))
      ;; Look for config file and recurse upward.
      (recur (if-let [config (dir-config dir)]
               (cons config configs)
               configs)
             (.getParentFile dir)
             (dec limit))
      ;; No further to recurse.
      configs)))