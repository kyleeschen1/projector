(ns projector.core
  (:require
   [projector.ast :refer [gen-env-from-file]]
   [projector.repl :refer [repl]]))

(defn -main []
  (let [env (gen-env-from-file "animations/sample.clj")]
    (repl env)))
