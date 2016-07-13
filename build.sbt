name := "peticodiac4J"

version := "1.0"
organization := "ca.uwaterloo.simplex"
javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-g:lines")

crossPaths := false
autoScalaLibrary := false
