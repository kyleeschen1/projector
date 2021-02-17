(ns projector.scratch.cursor)



;;================================================================================
;; Zprint

(require '[zprint.core :as zp])
(require '[clojure.string :as s])

(comment

  (defn strip-ansi [text]
    (let [marker #":cursor(.*):cursor" 
          new-text 
          (s/replace text (re-pattern  "\\\\[0m(.*?)\\\\[3.m") "$1")]
      (if (= new-text text)
        new-text
        (recur new-text))))


  (defn apply-cursor [text]
    (s/replace text marker (fn [text]
                             (->> text
                                  (last)
                                  (s/trim))))



    (zp/czprint '(+ 1 2 [:> 3])
                
                {:vector {:option-fn-first
                          #(if (= :>  %2)
                             {:color-map {:number :red
                                          :bracket :red
                                          :keyword :red}
                              }
                             {:color-map {:number :blue
                                          :bracket :red}
                              }
                             )}}))

  (def vec-map {:vector {:option-fn-first
                         #(if (= :>  %2)
                            {:color-map {:number :red
                                         :bracket :red
                                         :fn :red
                                         :keyword :red
                                         :paren :red}
                             }
                            
                            {:color-map {:number :blue
                                         :bracket :red}
                             }
                            )}})





  (if false
    (doseq [frame film-rec]
      (-> frame
          (zp/czprint-str  (merge
                            {:color-map {:keyword :red
                                         :bracket :red}
                             :fn-map {"cons" :arg1-force-nl
                                      "default" :arg1-force-nl}
                             }
                            vec-map))
          (s/replace #":>" "")
          (println)))))
