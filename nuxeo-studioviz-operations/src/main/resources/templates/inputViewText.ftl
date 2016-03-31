<#if tabs??>
Tabs:
<#list tabs as tab>
	${tab}
</#list>
</#if>

<#if docTypes??>
Documents:
<#list docTypes as docType>
	${docType}
</#list>
</#if>

<#if contentViews??>
Content Views:
<#list contentViews as contentView>
	${contentView}
</#list>
</#if>

<#if formLayouts??>
Form Layouts:
<#list formLayouts as formLayout>
	${formLayout}
</#list>
</#if>
