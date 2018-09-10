package com.takipi.common.api.data.view;

public class ViewInfo {
	public String id;
	public String name;
	public String description;
	public boolean shared;
	public ViewFilters filters;
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		
		if (name != null) {
			sb.append(name);
		}
		
		if (id != null) {
			sb.append("(");
			sb.append(id);
			sb.append(")");
		}
		
		if (sb.length() != 0) {
			return sb.toString();
		}
		
		return super.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if (this == obj) {
			return true;
		}
					
		if (obj instanceof ViewInfo) {
			ViewInfo other = (ViewInfo)obj;
			
			if ((this.id != null) && (other.id != null) && (id.equals(other.id))) {
				return true;
			}
		}

		return false;
	}
	
	@Override
	public int hashCode() {
		if (id != null) {
			return id.hashCode();
		}
		
		return super.hashCode();
	}
}
