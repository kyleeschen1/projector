(ns projector.style)

(def default-styles
  
  {:fn-map {"if" :arg1-force-nl
            "cons" :arg1-force-nl}
   
   :color-map {:keyword [:red]
               :fn [:bright-blue :bold]
               :true [:black]
               :false [:black]
               :number [:black]
               :paren [:black :bold]
               :symbol [:bright-blue :bold]
               :user-fn [:bright-blue :bold]
               }
   
   :list {:hang? true                             
          :indent-arg 5}})

(defn env->styles [env]
  (->> env
       keys
       (map (fn [k]
              [(str k) :gt2-force-nl]))
       (into {})
       (update-in default-styles [:fn-map] merge)))

