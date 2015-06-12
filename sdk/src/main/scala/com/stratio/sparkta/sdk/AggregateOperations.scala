/**
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparkta.sdk

import org.apache.spark.sql._

object AggregateOperations {

  def toString(dimensionValuesT: DimensionValuesTime, aggregations: Map[String, Option[Any]]): String =
    keyString(dimensionValuesT) +
      " DIMENSIONS: " + dimensionValuesT.dimensionValues.mkString("|") +
      " AGGREGATIONS: " + aggregations +
      " TIME: " + dimensionValuesT.time

  def keyString(dimensionValuesT: DimensionValuesTime): String =
    dimensionValuesNamesSorted(dimensionValuesT.dimensionValues)
      .filter(dimName => dimName.nonEmpty).mkString(Output.Separator)

  /*
  * By default transform an UpdateMetricOperation in a Row with description.
  * If fixedPrecisions is defined add fields and values to the original values and fields.
  * Id field we need calculate the value with all other values
  */
  def toKeyRow(dimensionValuesT: DimensionValuesTime,
               aggregations: Map[String, Option[Any]],
               fixedAggregation: Map[String, Option[Any]],
               fixedPrecisions: Option[Seq[(String, Any)]],
               idCalculated: Boolean,
               timeName : String): (Option[String], Row) = {
    val dimensionValuesFiltered = filterDimensionValuesByPrecision(dimensionValuesT.dimensionValues,
      if(timeName.isEmpty) None else Some(timeName))
    val namesDim = dimensionValuesNames(dimensionValuesFiltered.sorted)
    val (valuesDim, valuesAgg) = toSeq(dimensionValuesFiltered, aggregations ++ fixedAggregation)
    val (namesFixed, valuesFixed) = if (fixedPrecisions.isDefined) {
      val fixedPrecisionsSorted = fixedPrecisions.get.filter(fb => fb._1 != timeName)
        .sortWith((precision1, precision2) => precision1._1 < precision2._1)
      (namesDim ++ fixedPrecisionsSorted.map(_._1) ++ Seq(timeName),
        valuesDim ++ fixedPrecisionsSorted.map(_._2) ++
          Seq(DateOperations.millisToTimeStamp(dimensionValuesT.time)))
    } else (namesDim ++ Seq(timeName),
      valuesDim ++ Seq(DateOperations.millisToTimeStamp(dimensionValuesT.time)))
    val (keysId, rowId) = getNamesValues(namesFixed, valuesFixed, idCalculated)

    if (keysId.length > 0) (Some(keysId.mkString(Output.Separator)), Row.fromSeq(rowId ++ valuesAgg))
    else (None, Row.fromSeq(rowId ++ valuesAgg))
  }

  def toSeq(dimensionValues: Seq[DimensionValue], aggregations: Map[String, Option[Any]]): (Seq[Any], Seq[Any]) =
    (dimensionValues.sorted.map(dimVal => dimVal.value),
      aggregations.toSeq.sortWith(_._1 < _._1).map(aggregation => aggregation._2.getOrElse(0)))

  def getNamesValues(names: Seq[String], values: Seq[Any], idCalculated: Boolean): (Seq[String], Seq[Any]) =
    if (idCalculated && !names.contains(Output.Id))
      (Seq(Output.Id) ++ names, Seq(values.mkString(Output.Separator)) ++ values)
    else (names, values)

  def dimensionValuesNames(dimensionValues: Seq[DimensionValue]): Seq[String] = dimensionValues.map(_.getNameDimension)

  def dimensionValuesNamesSorted(dimensionValues: Seq[DimensionValue]): Seq[String] =
    dimensionValuesNames(dimensionValues.sorted)

  def filterDimensionValuesByPrecision(dimensionValues: Seq[DimensionValue], precisionName : Option[String])
  : Seq[DimensionValue] =
    precisionName match {
      case None => dimensionValues
      case Some(precision) => dimensionValues.filter(rollup => (rollup.getNameDimension != precision))
    }

}