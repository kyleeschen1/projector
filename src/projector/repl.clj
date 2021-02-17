
(ns projector.repl
  (:require
   [projector.ast :refer [evaluate script->frames]]
   [projector.style :refer [env->styles]]
   [clojure.pprint :as pp]
   [clojure.zip :as z]
   [zprint.core :as zp])
  (:import jline.console.ConsoleReader)
  (:gen-class))

(declare repl)
(declare clear-screen)
(declare print-header)
(declare catch-input-errors)


;; ================================================================================
;; Configure Projector


(defn projector [frames env]
  (let [styles (env->styles env)]

    (clear-screen)
    
    (doseq [frame frames]

      (print-header)
      
      (zp/czprint frame styles)
      
      (println "\n\n\n\n")
      (Thread/sleep 500)
      (clear-screen))) )


;; ================================================================================
;; Special Repl Inputs

(defn input->process [input history env]
  input)

(defmulti process-input input->process)

(defmethod process-input "(quit)"
  [input _ _]
  (clear-screen)
  (print-header)
  (println "Well, fine. Have a wonderful life.")
  (Thread/sleep 1000)
  (clear-screen))

(defmethod process-input "(clear)"
  [input history env]
  (repl [] env))

(defmethod process-input "(animate)"
  [input history env]
  (let [prior-input (-> history
                        butlast
                        butlast
                        last)] 
    (->  prior-input
         (evaluate env)
         :script
         (script->frames input [] [])
         (projector env)))
  (repl history env))


(defmethod process-input :default
  [input history env]
  (let [s-expr (read-string input) ]
    (repl (-> history
               (conj s-expr)
               (conj (try
                       (:result (evaluate s-expr env))
                       (catch Exception e (.getMessage e))))
               (conj :break))
           env)))



;; ================================================================================
;; Repl Helpers

(defn clear-screen []
  (print (str (char 27) "[2J"))
  (print (str (char 27) "[;H")))

(defn print-header []
  (println "===========================================")
  (println "Projector REPL")
  (println "===========================================")
  (println "\n"))


(defn validate [input]
  "My magnum opus."
  (let [major-security-threat? false]
    (if major-security-threat?
      nil
      input)))

(defmacro catch-input-errors [history env expr]
  `(try  
    ~expr   
    (catch Exception e#
      (repl  (-> ~history
                 (conj (str "Error: " (.getMessage e#)))
                 (conj :break))
             ~env))))

(defn print-last-n-lines-of-history [history n]
  (loop [last-n-lines (take-last n history)]
    (when-let [line (first last-n-lines)]
      (if (= :break line)
        (println "\n")
        (do
          (print "> ")
          (pp/pprint line)))
      (recur (rest last-n-lines)))))



(defn read-from-console [history]
  (do
    (print "> ")
    (flush)
    (validate (read-line))))


(defn repl

  ([env]

   (repl [] env))

  ([history env]

   (clear-screen) 
   (print-header)
   (println "Please enter expression:\n\n")   
   (print-last-n-lines-of-history history 8)

   (catch-input-errors  history
                        env
                        (if-let [input (read-from-console history)]
                          (process-input input history env)
                          (repl history env)))))
 






