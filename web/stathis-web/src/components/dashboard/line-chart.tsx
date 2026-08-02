'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  ResponsiveContainer,
  LineChart as RechartsLineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from 'recharts';
import { motion } from 'framer-motion';
import { TrendingUp } from 'lucide-react';

interface LineChartProps {
  title: string;
  description?: string;
  data: any[];
  categories: string[];
  /** Friendly tooltip / legend names keyed by dataKey */
  categoryNames?: Record<string, string>;
  index: string;
  colors?: string[];
  className?: string;
  yDomain?: [number, number];
  showLegend?: boolean;
  valueSuffix?: string;
}

export function LineChart({
  title,
  description,
  data,
  categories,
  categoryNames,
  index,
  colors = ['var(--primary)', 'var(--secondary)'],
  className,
  yDomain,
  showLegend = false,
  valueSuffix = '',
}: LineChartProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6 }}
    >
      <Card className={`rounded-2xl border-border/50 bg-card/80 backdrop-blur-xl shadow-lg hover:shadow-xl transition-all duration-300 h-full min-h-[280px] flex flex-col ${className}`}>
        <CardHeader className="pb-4">
          <CardTitle className="text-lg font-semibold flex items-center gap-2">
            <div className="relative">
              <div className="absolute -inset-1 rounded-full bg-gradient-to-br from-primary/20 to-secondary/20 blur-sm" />
              <TrendingUp className="relative h-5 w-5 text-primary" />
            </div>
            {title}
          </CardTitle>
          {description && <CardDescription className="text-sm">{description}</CardDescription>}
        </CardHeader>
        <CardContent className="flex-1 flex flex-col">
          {/* Explicit height: Recharts ResponsiveContainer with height=100% often paints 0px in flex layouts */}
          <div className="w-full" style={{ height: 240, minHeight: 240 }}>
            <ResponsiveContainer width="100%" height={240}>
              <RechartsLineChart data={data} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" opacity={0.3} />
                <XAxis
                  dataKey={index}
                  stroke="var(--muted-foreground)"
                  fontSize={12}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  stroke="var(--muted-foreground)"
                  fontSize={12}
                  tickLine={false}
                  axisLine={false}
                  domain={yDomain}
                  tickFormatter={(value) => `${value}${valueSuffix}`}
                />
                <Tooltip
                  formatter={(value: number, name: string) => [
                    `${value}${valueSuffix}`,
                    categoryNames?.[name] || name,
                  ]}
                  contentStyle={{
                    backgroundColor: 'var(--card)',
                    borderColor: 'var(--border)',
                    borderRadius: '12px',
                    boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)'
                  }}
                />
                {showLegend && (
                  <Legend
                    formatter={(value) => categoryNames?.[value] || value}
                    wrapperStyle={{ fontSize: 12 }}
                  />
                )}
                {categories.map((category, i) => (
                  <Line
                    key={category}
                    type="monotone"
                    dataKey={category}
                    name={categoryNames?.[category] || category}
                    stroke={colors[i % colors.length]}
                    strokeWidth={3}
                    dot={{ r: 5, fill: colors[i % colors.length] }}
                    activeDot={{ r: 7, stroke: colors[i % colors.length], strokeWidth: 2 }}
                  />
                ))}
              </RechartsLineChart>
            </ResponsiveContainer>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}
