(ns projector.scratch.keypress)

(comment

  (defn show-keystroke []
    (print "Enter a keystroke: ")
    (flush)
    (let [cr (ConsoleReader.)
          keyint (.readCharacter cr)]
      (println (format "Got %d ('%c')!" keyint (char keyint)))))



  (defn input->command [input string]
    (let [commands {13 (fn []
                         
                         (comment
                           (println string)
                           (-> string
                               validate
                               read-string))
                         (read))

                    508  (fn []
                           (println "\b")
                           ;;(flush)
                           (listen (s/join "" (drop-last string)))

                           46  (fn []
                                 (println "\b" )
                                 (flush)
                                 (listen (str string  "\b" ) ;;(s/join "" (drop-last string))
                                         ))

                           
                           
                           (if-let [command (get commands input)]
                             (command)
                             (do
                               (print (str (char input)))
                               (flush)
                               
                               (listen (str string (char input))))))}])


    (defn listen [string]
      (-> (ConsoleReader.)
          (.readCharacter)
          (input->command string)))))
