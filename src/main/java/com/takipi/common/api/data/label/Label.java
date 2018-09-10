package com.takipi.common.api.data.label;

public class Label {
	public String name;
	public String type;
	public String color;
	
	@Override
	public String toString() {
		if (name != null) {
			return name;
		}
		
		return super.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		
		if (this == obj) {
			return true;
		}
					
		if (obj instanceof Label) {
			Label other = (Label)obj;
			
			if ((this.name != null) && (other.name != null) && (name.equals(other.name))) {
				return true;
			}
		}

		return false;
	}
	
	@Override
	public int hashCode() {
		if (name != null) {
			return name.hashCode();
		}
		
		return super.hashCode();
	}
}
