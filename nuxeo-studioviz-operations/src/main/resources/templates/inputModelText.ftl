 <#if schemas??>
Schemas:
 <#list schemas as schema>
 	${schema}
 </#list>
 </#if>

 <#if docTypes??>
Documents:
 <#list docTypes as docType>
 	${docType}
 </#list>
 </#if>

 <#if facets??>
Facets:
 <#list facets as facet>
 	${facet}  	
 </#list>
 </#if>
