name := "glossary"

version := "1.0"

scalaVersion := "2.11.11"

resolvers += "peoplepattern" at "https://dl.bintray.com/peoplepattern/maven"

libraryDependencies += "com.peoplepattern" %% "lib-text" % "0.3.2"
libraryDependencies +="com.typesafe.akka" %% "akka-http" % "10.0.5"
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5"
libraryDependencies += "com.norbitltd" %% "spoiwo" % "1.2.0"