import com.cwctravel.hudson.plugins.extended_choice_parameter.ExtendedChoiceParameterDefinition

def multiSelect(list, list_name, project_name) {
	def multiSelect = new ExtendedChoiceParameterDefinition(list_name, 
			"PT_MULTI_SELECT", 
			list, 
			project_name,
			"", 
			"",
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			list, 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			"", 
			false,
			false, 
			30, 
			"multiselect", 
			",");
	return multiSelect;
}
