(ns ClojureSprites.core
	(:require [clojure.java.io :as io]
			  [clojure.string :as string]
			  [clojure.pprint :as pp])
	(:use [clojure.java.shell :only [sh]]))

; These will be command line arguments in the final build
(def pathprefix "/var/www/html/announcemedia/answers.com/rubix.answers.com/htdocs/")
(def css-infile (str pathprefix "/includes/css/main.css"))
(def css-outfile css-infile)
(def spritefile "/includes/images/sprite.png")
(def tmp-img-dir "/tmp/clj_sprite_imgages")

(defn maxoffset [images i]
"Takes a list of vectors and returns the vector with the maximum value at a given index."
	(reduce 
		(fn [img1 img2]
			(if (> (nth img2 i 0) (nth img1 i 0))
				img2
				img1
			))
		images))

(defn metadata [files]
"Adds width and height data to the incoming list of image paths."
	(map 
		(fn [file]
			(try
				(with-open [r (java.io.FileInputStream. (str pathprefix file))]
           			(let [img (javax.imageio.ImageIO/read r)] [file (.getWidth img) (.getHeight img)]))
				(catch Exception e (prn (str "Can't find file: " file ". We'll ignore that image in CSS.")))))
		files))

(defn strip-leading-slash [filepath]
	(if (.startsWith filepath "/")
		(subs filepath 1)
		filepath
		))

(defn findpath [url]
"Strips off any domain or protocol in the image url."
	(if (nil? (re-find #"http://" url))
		(strip-leading-slash url)
		(let [[domain filepath] (string/split (string/replace url "http://" "") #"/" 2)]
			(strip-leading-slash filepath)
		)))

(defn processfile [file-name resultfn]
"Reads in entire CSS file and regexes out any url paths to images."
	(map resultfn (distinct (re-seq (re-pattern (str "url\\([\"']?([\\w\\-_\\d:\\/\\/\\.]+)[\"']?\\)")) (slurp file-name)))))

(defn process-img [line]
"The process function for the file reader."
	(let [[match url] line]
		(findpath url)))

(defn updatecss [images sprite infile outfile]
"Loops over images, increments height offset while updating CSS."
	(def css (slurp infile))
	(def offset (int 0))
	(doseq [img (filter (fn [x] (not= (nil? x) true)) images)]
		(def regex (re-pattern (str "(?im)^(.*)url\\(.*" (nth img 0) "[\"']?\\)(.*)$")))
		(def css (string/replace 
			css
			regex
			(str "$1 url('" sprite "')$2\nbackground-position: 0px -" offset "px !important;")
		))
		(prn img)
		(prn (str offset "+" (nth img 2 0)))
		(def offset (+ offset (nth img 2 0))))
	(spit outfile css))

(defn create-sprite [images outfile]
"Creates and runs an imagemagick command based on the list of images."
	(sh "mkdir" tmp-img-dir)
	(doseq [img (filter (fn [x] (not= (nil? x) true)) images)]
		(prn (str tmp-img-dir "/" (format "%08d" (.indexOf images img)) "-" (clojure.string/replace (nth img 0) #"/" "_")))
		(sh "cp" (str pathprefix (nth img 0)) (str tmp-img-dir "/" (format "%08d" (.indexOf images img)) "-" (clojure.string/replace (nth img 0) #"/" "_"))))
	(prn "Running imagemagick command...")
	(if (= 0 (:exit (sh "convert" (str tmp-img-dir "/*") "-append" outfile)))
		(sh "rm" "-rf" tmp-img-dir)))
		;(def foo true)))

(defn -main [css-infile]
"Program start."
	(def images (metadata (processfile css-infile process-img)))
	(updatecss images spritefile css-infile css-outfile)
	(prn "Your CSS file has been updated. Please wait while we build the sprite image.")
	(create-sprite images (str pathprefix spritefile))
	(prn "Done!"))

