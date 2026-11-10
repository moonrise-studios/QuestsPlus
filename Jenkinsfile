pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        buildDiscarder(logRotator(numToKeepStr: '25'))
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        // The build compiles with a Java 21 toolchain (plugin/build.gradle.kts, sdk/build.gradle.kts),
        // so the agent needs a JDK 21 that Gradle can find. Gradle itself runs on Java 21 or newer.
        stage('Verify Java 21') {
            steps {
                sh '''
                    set -eu

                    if ! command -v java >/dev/null 2>&1; then
                        echo "QuestsPlus requires Java 21, but Java is not installed on this Jenkins agent." >&2
                        exit 1
                    fi

                    java_major="$(java -XshowSettings:properties -version 2>&1 \
                        | sed -n 's/^[[:space:]]*java.version = \\([0-9][0-9]*\\).*/\\1/p')"

                    if [ -z "$java_major" ] || [ "$java_major" -lt 21 ]; then
                        echo "QuestsPlus requires Java 21 or newer. The Jenkins agent reports:" >&2
                        java -version
                        exit 1
                    fi

                    java -version
                '''
            }
        }

        // The jars are built and archived first, without tests, so they are available within seconds:
        // QuestsPlus.jar (the shaded plugin, in build/libs) and the SDK jars (sdk/build/libs:
        // sdk-<version>.jar plus its sources and javadoc jars).
        // No `clean`: Gradle's up-to-date checks keep incremental builds correct and skip work that did not change.
        stage('Build') {
            steps {
                sh './gradlew --no-daemon --console=plain :plugin:shadowJar :sdk:assemble'
                archiveArtifacts artifacts: 'build/libs/QuestsPlus*.jar, sdk/build/libs/*.jar', fingerprint: true
            }
        }

        // Everything else `build` does: the SDK and plugin unit tests (including the ArchUnit layer check)
        // and the remaining checks. A failure here fails the run.
        stage('Test') {
            steps {
                sh './gradlew --no-daemon --console=plain build'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
        }
    }
}
