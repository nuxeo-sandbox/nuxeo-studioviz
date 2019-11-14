digraph BL {
  graph [fontname = "helvetica", fontsize=11];
  node [fontname = "helvetica", fontsize=11];
  edge [fontname = "helvetica", fontsize=11];

  <#list nodes as node>
  <#assign json = node?eval>
  "${json.name}" [<#if json.featureName??>URL="https://connect.nuxeo.com/nuxeo/site/studio/ide?project=${studioProjectName}#@feature:${json.featureName}", </#if>label="${json.labelName?js_string}",shape=box,fontcolor=white,color="${json.color}",fillcolor="${json.color}",style="filled"];
  </#list>

  <#list transitions as transition>
  ${transition};
  </#list>

  <#if userActions??>
  subgraph cluster_0 {
  	style="dashed";
   	label = "User Actions";
    ${userActions};
  }
  </#if>

  <#if automationChainsAndScripting??>
  subgraph cluster_1 {
  	style="dashed";
   	label = "Automation Chains & Scriptings";
    ${automationChainsAndScripting};
  }
  </#if>

  <#if events??>
  subgraph cluster_2 {
  	style="dashed";
   	label = "Events";
    ${events};
  }
  </#if>

  <#if wfTasks??>
  subgraph cluster_3 {
  	style="dashed";
   	label = "Workflow Tasks";
    ${wfTasks};
  }
  </#if>

}
