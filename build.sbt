name := "glossary"

version := "1.0"

scalaVersion := "2.11.12"

resolvers += "peoplepattern" at "https://dl.bintray.com/peoplepattern/maven"

libraryDependencies += "com.peoplepattern" %% "lib-text"             % "0.3"
libraryDependencies += "com.typesafe.akka" %% "akka-http"            % "10.1.5"
libraryDependencies += "com.typesafe.akka" %% "akka-stream"          % "2.5.12"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5"
libraryDependencies += "com.norbitltd"     %% "spoiwo"               % "1.2.0"
