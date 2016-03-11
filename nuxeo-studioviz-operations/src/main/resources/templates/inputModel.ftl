digraph M {
  graph [fontname = "helvetica", fontsize=11];
  node [fontname = "helvetica", fontsize=11];
  edge [fontname = "helvetica", fontsize=11];

  <#list nodes as node>
  <#assign json = node?eval>
  ${json.name} [<#if json.featureName??>URL="https://connect.nuxeo.com/nuxeo/site/studio/ide?project=${studioProjectName}#@feature:${json.featureName}", </#if>label="${json.labelName}",shape=box,fontcolor=white,color="${json.color}",fillcolor="${json.color}",style="filled"];
  </#list>

  <#list transitions as transition>
  ${transition};
  </#list>

  <#if schemas??>
  subgraph cluster_0 {
  	style="dashed";
   	label = "Schemas";
    ${schemas};
  }
  </#if>

  <#if docTypes??>
  subgraph cluster_1 {
  	style="dashed";
   	label = "Document Types";
    ${docTypes};
  }
  </#if>

  <#if facets??>
  subgraph cluster_2 {
  	style="dashed";
   	label = "Facets";
    ${facets};
  }
  </#if>

}
