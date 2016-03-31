<#if automationChainsAndScripting??>
Automation Chains And Scripting
<#list automationChainsAndScripting as automation>
	${automation}
</#list>
</#if>
  
<#if events??>
Events:
<#list events as event>
	${event}
</#list>
</#if>

<#if wfTasks??>
Workflow Tasks:
<#list wfTasks as wfTask>
	${wfTask}
</#list>
</#if>