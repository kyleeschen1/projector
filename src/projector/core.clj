(ns projector.core
  (:require
   [projector.ast :refer [gen-env-from-file]]
   [projector.repl :refer [repl]]))

(defn -main [file]
  (let [env (gen-env-from-file file)]
    (repl env)))
