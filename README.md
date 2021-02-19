# Projector
### Bringing Abstraction to Life

*"Lisp has been jokingly described as "the most intelligent way to misuse a computer". I think that description a great compliment because it transmits the full flavor of liberation: it has assisted a number of our most gifted fellow humans in thinking previously impossible thoughts." - Edgar Dijkstra*

Lisp dialects are beautiful but syntactically-intimidating, leading many to dismiss them. This is a shame because Lisps are excellent pedagogical vehicles. Many classic textbooks in programming and artificial intelligence ([SICP](https://www.amazon.com/Structure-Interpretation-Computer-Programs-Engineering/dp/0262510871/ref=pd_lpo_14_t_2/139-8855044-5241522?_encoding=UTF8&pd_rd_i=0262510871&pd_rd_r=6b59d653-6698-4867-b82f-9c081a881132&pd_rd_w=yjFXI&pd_rd_wg=YULvH&pf_rd_p=16b28406-aa34-451d-8a2e-b3930ada000c&pf_rd_r=XE210ZNMV225Y09XRQ3X&psc=1&refRID=XE210ZNMV225Y09XRQ3X), [PAIP](https://github.com/norvig/paip-lisp), [The Little Schemer](https://www.amazon.com/Little-Schemer-Daniel-P-Friedman/dp/0262560992), etc.) use Lisp throughout. SICP alone proves that Lisp can serve as a kind of programming Rosetta Stone, as it uses just a few primitives to implement the major paradigms of computer science. However, these texts are inaccessible to those who find Lisp to be indecipherable and / or infuriating.

To make the language more accessible, I am trying to create a program that will allow teachers to easily animate Lisp evaluation, so students can see how the abstract syntax trees unfurl, contract, and self-modify in real time. 

My goal is to do an animated YouTube walkthrough of SICP - just one ignoramous enlightening others, but with excellent graphics. This current version is limited (with known issues recorded next to the [code itself](https://github.com/kyleeschen1/projector/blob/main/src/projector/ast.clj)), and geared toward illustrating general programming principles rather than idiomatic Clojure. However, I do think that it can serve as a good MVP.

## Demo

To make a function available for animation, you define it in the animation directory. Right now, the interpreter can parse only non-variadic functions, but given that the formatting is identical.

![alt text](https://github.com/kyleeschen1/projector/blob/main/images/Functions.png)

When you run the app, a very simple REPL launches. It does the usual REPL thing. 

![alt text](https://github.com/kyleeschen1/projector/blob/main/images/map.gif)

When you call ```(animate)```, the REPL will then flicker through the evaluation of the previous line, step by step. It can automatically walk through a small subset of Clojure, which can be further extended by adding to the above mentioned animation file.

I see this used primarily as a teaching tool, where abstractions are made vivid through its depiction as an unfolding process. For instance, below we see how two different approaches to writing a factorial procedure (recursive and iterative) will consume stack space, depicted by depth.

![alt text](https://github.com/kyleeschen1/projector/blob/main/images/factorials.gif)

Teachers can create assignments where students must solve problems within a constrained subset of Clojure functions. Students will be able to walk through code step by step to understand how all the functions fit together. I next want to make the animations responsive to key inputs, so that students can effortlessly stop, slow, speed, and rewind the animations. This allows students to zero in on the exact point where understanding breaks down, which in turn affords the kind of hyperfocused tuning that deliberate practice requires.


## How it Works

Behind the scenes, a "probe" catches a ride on an s-expression, and gathers evaluation data as it zips around the interpreter. This allows it to create an indexed "script" of the evaluation. Each index corresponds to a node in the AST.

![alt text](https://github.com/kyleeschen1/projector/blob/main/images/script.gif)

One can filter, map, assoc, etc. over this script to customize the animations. You then feed the original expression, an environment, and the script into another function that will generate intermediate states of the program (or at least those covered by my currently impotent interpreter):

![alt text](https://github.com/kyleeschen1/projector/blob/main/images/frames.gif)

This list of "frames" constitute a little film reel of the animation. Each is already indexed, and can be turned into a zipper so that students can stop the animation and zoom around to various components. (This part will require I add the keypress portion discussed above.)


## Other Uses

* Perhaps these animations could be used to create particularly vivid flashcards for spaced repetition.

* I've yet to see good visualizations of refactoring, and of the "abstraction process" in general. I can envision extending the script indices so that common patterns (operations, operation types, tree structures, etc.) can be filtered for, appeneded, etc. The teacher can then take a bunch of expressions and manipulate opacity to let differences fade, making shared structure vivid. The shared structure then fades so that the differences are made salient. To me, that's the essence of structure, and of abstraction in general: what varies, and what stays put? What degrades under transformation, and what survives? Those define an abstraction's borders. 

  * One can envision a game-like curriculum where students try and "spot the similarities" in expressions, and the strobing provides rapid feedback about the patterns they did and did not detect.

  * This is more speculative, but perhaps machine learning could "suggest" code regions ripe for refactoring by detecting and highlighting shared structure. Programmers can consider and apply the suggestions that make sense. The strobing allows for a succint and precise depiction of the pattern that the machine wishes to convey.

* A more practical case: Whenever I start looking through a new repo on GitHub, I am confronted with a bunch of new functions defined in terms of new functions. Everything seems circular - I don't know where to start and where to go. I ulimately hope to write an interpreter that works on the AST output of clojure.tools.analyzer, so that I can download *any* Clojure library, and do a gradual stroll through the evalaution tree, step by step, seeing how how all the pieces fit together.


## License

Copyright © 2021 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
