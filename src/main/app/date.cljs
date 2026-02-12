(ns app.date
  (:require [clojure.string :as str]))

(defn- parse-yyyy-mm-dd [s]
  (when-let [[_ year month day] (re-matches #"(\d{4})-(\d{2})-(\d{2})" s)]
    (let [y (js/parseInt year)
          m (js/parseInt month)
          d (js/parseInt day)
          date (js/Date. y (dec m) d)] ; months are 0-indexed
      ;; Verify it's a valid date (js/Date accepts invalid dates like Feb 31)
      (when (and (= (.getFullYear date) y)
                 (= (.getMonth date) (dec m))
                 (= (.getDate date) d))
        s))))

(defn- format-date [date]
  (let [year (.getFullYear date)
        month (-> (.getMonth date) inc (str) (.padStart 2 "0"))
        day (-> (.getDate date) (str) (.padStart 2 "0"))]
    (str year "-" month "-" day)))

(defn- parse-slash-date [s prefer-dmy?]
  (when-let [[_ p1 p2 p3] (re-matches #"(\d{1,2})/(\d{1,2})/(\d{4})" s)]
    (let [[day month year] (if prefer-dmy?
                             [p1 p2 p3]
                             [p2 p1 p3])
          d (js/parseInt day)
          m (js/parseInt month)
          y (js/parseInt year)
          date (js/Date. y (dec m) d)]
      (when (and (= (.getFullYear date) y)
                 (= (.getMonth date) (dec m))
                 (= (.getDate date) d))
        (format-date date)))))

(defn parse-date
  "Parse date from YYYY-MM-DD, DD/MM/YYYY, or MM/DD/YYYY.
   Returns YYYY-MM-DD string or nil.
   
   For slash formats, tries to infer which is day vs month:
   - If one value > 12, assumes that's the day (DD/MM/YYYY)
   - Otherwise defaults to MM/DD/YYYY (US format)"
  [s]
  (when (string? s)
    (let [s (str/trim s)]
      (or
       ;; Try YYYY-MM-DD first (unambiguous)
       (parse-yyyy-mm-dd s)

       ;; Try slash formats
       (when-let [[_ p1 p2 p3] (re-matches #"(\d{1,2})/(\d{1,2})/(\d{4})" s)]
         (let [n1 (js/parseInt p1)
               n2 (js/parseInt p2)]
           (cond
             ;; If first number > 12, must be DD/MM/YYYY
             (> n1 12)
             (parse-slash-date s true)

             ;; If second number > 12, must be MM/DD/YYYY
             (> n2 12)
             (parse-slash-date s false)

             ;; Ambiguous - try DD/MM/YYYY first (international standard)
             ;; then fall back to MM/DD/YYYY
             :else
             (or (parse-slash-date s true)
                 (parse-slash-date s false)))))))))