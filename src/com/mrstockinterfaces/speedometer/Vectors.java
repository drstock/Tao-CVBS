package com.mrstockinterfaces.speedometer;

class Vector3f
{
	public float x;
	public float y;
	public float z;
	
	public Vector3f()
	{
		this.x = 0.f;
		this.y = 0.f;
		this.z = 0.f;
	}
	
	public Vector3f(float[] values)
	{
		this.set(values);
	}
	
	public Vector3f(float x, float y, float z)
	{
		this.set(x, y, z);
	}
	
	public Vector3f(Vector3f vec)
	{
		this.set(vec);
	}
	
	public void set(float x, float y, float z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public void set(float[] values)
	{
		this.x = values[0];
		this.y = values[1];
		this.z = values[2];
	}

	public void set(Vector3f vec)
	{
		this.x = vec.x;
		this.y = vec.y;
		this.z = vec.z;
	}
	
	public Vector3f copy()
	{
		return new Vector3f(this);
	}
	
	public float mag()
	{
		return (float)Math.sqrt(this.dot(this));
	}
	
	public float dot(Vector3f that)
	{
		return ((this.x * that.x) + (this.y * that.y) + (this.z * that.z));
	}
	
	public Vector3f unit()
	{
		float mag = this.mag();
		if (mag > 0.f)
		{
			return this.s_div(this.mag());
		}
		
		return new Vector3f(0.f, 0.f, 0.f);
	}
	
	public double angle(Vector3f that)
	{
		return Math.acos(this.unit().dot(that.unit()));
	}
	
	public Vector3f cross(Vector3f that)
	{
		float[] cross = new float[3];
		cross[0] = (this.y * that.z) - (this.z * that.y);
		cross[1] = (this.z * that.x) - (this.x * that.z);
		cross[2] = (this.x * that.y) - (this.y * that.x);
		return new Vector3f(cross);
	}
	
	public Vector3f sum(Vector3f that)
	{
		return new Vector3f(this.x + that.x, this.y + that.y, this.z + that.z);
	}
	
	public Vector3f diff(Vector3f that)
	{
		return new Vector3f(this.x - that.x, this.y - that.y, this.z - that.z);
	}

	public Vector3f s_mult(float factor)
	{
		return new Vector3f(this.x * factor, this.y * factor, this.z * factor);
	}
	
	public Vector3f s_div(float factor)
	{
		return this.s_mult(1.f / factor);
	}
	
	public Vector3f r_mult(Vector3f that)
	{
		return new Vector3f(this.x * that.x, this.y * that.y, this.z * that.z);
	}
	
	public Vector3f r_div(Vector3f that)
	{
		return new Vector3f(this.x / that.x, this.y / that.y, this.z / that.z);
	}
	
	public Vector3f proj(Vector3f that)
	{
		Vector3f u = that.unit();
		return u.s_mult(this.dot(u));
	}
	
	public Vector3f rej(Vector3f that)
	{
		return this.diff(this.proj(that));
	}

	public Vector3f pow(float exp)
	{
		return new Vector3f((float)Math.pow(this.x, exp), (float)Math.pow(this.y, exp), (float)Math.pow(this.z, exp));
	}
	
	public float[] to_pol()
	{
		float pol[] = new float[3];
		
		pol[0] = this.mag();
		pol[1] = (float)Math.atan2(Math.sqrt((this.x * this.x) + (this.y * this.y)), this.z);
		pol[2] = (float)Math.atan2(this.y, this.x);
		
		return pol;
	}
	
	public void from_pol(float[] pol)
	{
		this.x = (float)(pol[0] * Math.sin(pol[1]) * Math.cos(pol[2]));
		this.y = (float)(pol[0] * Math.sin(pol[1]) * Math.sin(pol[2]));
		this.z = (float)(pol[0] * Math.cos(pol[1]));
	}
}

class AveragedVector3f extends Vector3f
{
	public int count = 0;
	public int limit = (Integer.MAX_VALUE - 1);
	
	public void accum(Vector3f vec)
	{
		int new_count = this.count + 1;
		this.x = ((this.x * this.count) + vec.x) / new_count;
		this.y = ((this.y * this.count) + vec.y) / new_count;
		this.z = ((this.z * this.count) + vec.z) / new_count;
		
		if (this.count < this.limit)
		{
			this.count = new_count;
		}
	}
	
	public void accum(float[] values)
	{
		this.accum(new Vector3f(values));
	}
	
	public void accum(float x, float y, float z)
	{
		this.accum(new Vector3f(x, y, z));
	}
	
	public void reset()
	{
		this.count = 0;
	}
	
	public void set(float x, float y, float z)
	{
		this.count = 1;
		super.set(x, y, z);
	}
	
	public void set(float[] values)
	{
		this.count = 1;
		super.set(values);
	}
	
	public void set(Vector3f vec)
	{
		this.count = 1;
		super.set(vec);
	}
	
	public void set_limit(int limit)
	{
		if ((limit < 1) || (limit == Integer.MAX_VALUE))
		{
			throw new Error("Illegal value for limit: " + String.format("%d", limit));
		}
		
		this.limit = limit;
	}
	
	public boolean at_limit()
	{
		return (this.count == this.limit);
	}
}


class Vector3fIirFilter extends Vector3f
{
	public int count = 0;
	public int limit = (Integer.MAX_VALUE - 1);
	
	private Vector3f iv = new Vector3f(0.f, 0.f, 0.f);
	
	public Vector3fIirFilter()
	{
		super();
	}
	
	public Vector3fIirFilter(int limit)
	{
		super();
		this.set_limit(limit);
	}
	
	private void filter()
	{
		int new_count = this.count + 1;
/*		this.x = ((this.x * this.count) + this.iv.x) / new_count;
		this.y = ((this.y * this.count) + this.iv.y) / new_count;
		this.z = ((this.z * this.count) + this.iv.z) / new_count; */

		float[] this_pol = this.to_pol();
		float[] iv_pol = this.iv.to_pol();
		this_pol[0] = ((this_pol[0] * this.count) + iv_pol[0]) / new_count;
		this_pol[1] = ((this_pol[1] * this.count) + iv_pol[1]) / new_count;
		this_pol[2] = ((this_pol[2] * this.count) + iv_pol[2]) / new_count;
		this.from_pol(this_pol);
		
		if (this.count < this.limit)
		{
			this.count++;
		}
	}
	
	public void in(float x, float y, float z)
	{
		this.iv.set(x, y, z);
		this.filter();
	}
	
	public void in(float[] values)
	{
		this.iv.set(values);
		this.filter();
	}
	
	public void in(Vector3f vec)
	{
		this.iv.set(vec);
		this.filter();
	}
	
	public Vector3f lp_out()
	{
		return this.copy();
	}
	
	public Vector3f hp_out()
	{
		return this.iv.diff(this);
	}
	
	public void reset()
	{
		this.count = 0;
		this.iv.set(0.f, 0.f, 0.f);
	}	
	
	public void set(float x, float y, float z)
	{
		this.count = 1;
		super.set(x, y, z);
	}
	
	public void set(float[] values)
	{
		this.count = 1;
		super.set(values);
	}
	
	public void set(Vector3f vec)
	{
		this.count = 1;
		super.set(vec);
	}
	
	public void set_limit(int limit)
	{
		if ((limit < 1) || (limit == Integer.MAX_VALUE))
		{
			throw new Error("Illegal value for limit: " + String.format("%d", limit));
		}
		
		this.limit = limit;
	}
	
	public boolean at_limit()
	{
		return (this.count == this.limit);
	}
}