{{- define "questgrow.name" -}}questgrow{{- end -}}

{{- define "questgrow.labels" -}}
app.kubernetes.io/name: questgrow
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: questgrow-{{ .Chart.Version }}
{{- end -}}

{{- define "questgrow.selectorLabels" -}}
app.kubernetes.io/name: questgrow
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "questgrow.image" -}}
{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}
{{- end -}}
