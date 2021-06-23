def buildImage() {
    sh 'gcloud auth activate-service-account --key-file=${GCE_SERVICE_ACCOUNT_KEY}'
    sh 'gcloud auth configure-docker'
    sh 'utils/jenkins/build_image.sh'
}

pipeline {
    agent { label "jnlp_dind_kubectl" }

    environment {
        CLOUDSDK_PYTHON = "/usr/bin/python"
        GCP_PROJECT_ID = "viralize-ci"
        IMAGE_NAME = "eu.gcr.io/$GCP_PROJECT_ID/druid"
        TARGET_IMAGE = "$IMAGE_NAME:$GIT_COMMIT"
        GCE_SERVICE_ACCOUNT_KEY = credentials('CI_GCR_SERVICE_ACCOUNT')
    }

    stages {
        stage('Build image') {
            steps {
                script {
                    buildImage()
                }
            }
        }
    }
}
