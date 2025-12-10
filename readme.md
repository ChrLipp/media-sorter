# Media sorter
Sorts iPhone media files into monthly directories.

- Based on Java 21
- written in [Kotlin](https://kotlinlang.org/)
- powered by [Spring Boot](https://spring.io/projects/spring-boot)
- build with [Gradle](https://gradle.org/)
- Command line handling with [Picocli](https://picocli.info/), the annotations are processed at compile time

## Command line documentation
- -e, --examine=<testFile>          Examine single file 
- -h, --help                        Show this help message and exit.
- -i, --input=<inputDirectory>      Input directory
- -o, --output=<outputDirectory>    Output directory
- -t, --test                        Simulation mode, no movement
- -V, --version                     Print version information and exit.

## Gradle usage
- Building `./gradlew build`
- Running `./gradlew bootRun`
- Testing `./gradlew test`
- Dependency updates `./gradlew dependencyUpdates`, change version numbers in `gradle.properties`

## Documentation
- HEIC to JPEG conversation
  See https://blog.jjhayes.net/wp/2020/09/03/open-source-heic-to-jpg-conversion/
  `magick mogrify -format JPG *.HEIC`

## How to sort files
- Run program to move (and convert) iPhone files
  - from '/Users/christian/Pictures/Christians iPhone'
  - to /Users/christian/Pictures/Destination
- Copy files 
  - from /Users/christian/Pictures/Destination
  - to /Volumes/christian/ToSort
- Copy each year local to  'D:/Gemeinsam/Bilder'
- Fix everything
- Copy the year to the NAS

