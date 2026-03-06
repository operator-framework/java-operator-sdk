{{/*
Expand the name of the chart.
*/}}
{{- define "josdk-operator.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "josdk-operator.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label.
*/}}
{{- define "josdk-operator.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "josdk-operator.labels" -}}
helm.sh/chart: {{ include "josdk-operator.chart" . }}
{{ include "josdk-operator.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels used in Deployment and Service selectors.
*/}}
{{- define "josdk-operator.selectorLabels" -}}
app.kubernetes.io/name: {{ include "josdk-operator.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "josdk-operator.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "josdk-operator.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Deployment namespace.
*/}}
{{- define "josdk-operator.namespace" -}}
{{- default .Release.Namespace .Values.namespace }}
{{- end }}

{{/*
Name of the JOSDK config ConfigMap.
*/}}
{{- define "josdk-operator.configMapName" -}}
{{- default (printf "%s-config" (include "josdk-operator.fullname" .)) .Values.josdkConfig.configMapName }}
{{- end }}

{{/*
Name of the log4j2 ConfigMap.
*/}}
{{- define "josdk-operator.log4j2ConfigMapName" -}}
{{- default (printf "%s-log4j2" (include "josdk-operator.fullname" .)) .Values.log4j2.configMapName }}
{{- end }}

{{/*
JAVA_TOOL_OPTIONS / JVM args value.
Appends the log4j2 config file system property automatically when log4j2 is enabled.
*/}}
{{- define "josdk-operator.jvmArgs" -}}
{{- $args := .Values.jvmArgs | default "" }}
{{- if .Values.log4j2.enabled }}
{{- $args = printf "%s -Dlog4j2.configurationFile=%s/log4j2.xml" $args .Values.log4j2.mountPath | trim }}
{{- end }}
{{- $args }}
{{- end }}
